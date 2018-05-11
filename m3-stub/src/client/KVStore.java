package client;

import app_kvClient.KVClient;
import common.datatypes.MD5;
import common.datatypes.MetaData;
import common.KVMessage;
import common.KVMessage.StatusType;
import common.communication.KVCommunication;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;

public class KVStore implements KVCommInterface {

    private static Logger logger = Logger.getRootLogger();
    private Socket ClientSocket;
    private String ServerAddress;
    private int portNumber;
    private OutputStream output;
    private InputStream input;

    private KVCommunication communicator;
    private boolean isClient = false;

    private static String replicaOneAdd;
    private static int replicaOnePort;

    private static String replicaTwoAdd;
    private static int replicaTwoPort;

    private  boolean stopped = true;

    private TreeMap<BigInteger, MetaData> metaData;

    private String Helper_key=null;

	/**
	 * Initialize KVStore with address and port of KVServer
	 * @param address the address of the KVServer
	 * @param port the port of the KVServer
	 */
    public KVStore(String address, int port) {
        ServerAddress = address;
        portNumber = port;
        logger.debug("KVStore Initialized");
    }

    public KVStore(String address, int port, boolean Client) {
        ServerAddress = address;
        portNumber = port;
        isClient = Client;
    }
    public KVStore(String address, int port, String kvcl)
    {
        ServerAddress = address;
        portNumber = port;
        Helper_key=kvcl;
        isClient=true;
    }

    public class KVStore_Helper implements Runnable {
        private String helper_ServerAddress;
        private int helper_portNumber;
        private KVCommunication helper_communicator;
        private Socket helper_ClientSocket;
        private OutputStream helper_output;
        private InputStream helper_input;

        private int type=0;
        private String key;
        private  KVStore store;
        private KVStore_Helper(String address, int port,KVStore stor) {
            helper_ServerAddress = address;
            helper_portNumber = port;
            type=0;
            this.key=null;
            this.store=stor;
        }
        private KVStore_Helper(String address, int port,String k,KVStore stor) {
            helper_ServerAddress = address;
            helper_portNumber = port;
            type=1;
            this.key=k;
            this.store=stor;
        }

        private void helper_connect() throws Exception {
            helper_ClientSocket = new Socket(helper_ServerAddress, helper_portNumber);
            //System.out.println(helper_ClientSocket.toString());
            try {
                helper_output = helper_ClientSocket.getOutputStream();
                helper_input = helper_ClientSocket.getInputStream();

                helper_communicator = new KVCommunication(helper_ClientSocket, null);
            } catch (Exception e) {
                System.out.println("Helper_thread connect error");
            }
        }

        public void run() {
            try {
                helper_connect();
            } catch (Exception e) {
                System.out.println("helper_thread run error");
            }

            if(type==0)//Replica communicating channel
            {
                while (!store.stopped && portNumber == helper_portNumber) {
                    try {
                        helper_communicator.sendKVMessage(StatusType.GET, "GetReplica", "", KVMessage.CommunicationType.ECS_GetReplica);
                        KVMessage message = helper_communicator.receiveKVMessage();

                        if (message.getValue() != null && message.getCommunicationType() == KVMessage.CommunicationType.ECS_GetReplica) {
                            String[] tokens = message.getValue().split(";");

                            if (tokens.length == 2) {
                                if (replicaOnePort != Integer.parseInt(tokens[1])) {
                                    replicaOneAdd = tokens[0];
                                    replicaOnePort = Integer.parseInt(tokens[1]);
                                }
                            } else if (tokens.length == 4) {
                                if (replicaTwoPort != Integer.parseInt(tokens[3]) || replicaOnePort != Integer.parseInt(tokens[1])) {
                                    replicaOneAdd = tokens[0];
                                    replicaOnePort = Integer.parseInt(tokens[1]);
                                    replicaTwoAdd = tokens[2];
                                    replicaTwoPort = Integer.parseInt(tokens[3]);
                                }
                            }
                        }

                    } catch (IOException ioe) {
                        System.out.println("Helper_thread send error");
                        return;
                    }

                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        System.out.println("Helper_thread sleep error");
                    }
                }
            }
            else if(type==1)
            {
                try {
                    helper_communicator.sendKVMessage(StatusType.GET, this.helper_ClientSocket.toString(), this.key, KVMessage.CommunicationType.ECS_InitWatch);
                    KVMessage message = helper_communicator.receiveKVMessage();
                    if(message.getCommunicationType()== KVMessage.CommunicationType.ECS_Watch_Failure) {
                        logger.error("KEY does not exist, thread close");
                        return;

                    }
                }catch (IOException ioe)
                {
                    System.out.println("Helper_thread init watch fail");
                    return;
                }
                while(!store.stopped)
                {
                    try {
                        helper_communicator.sendKVMessage(StatusType.GET, this.helper_ClientSocket.toString(), this.key, KVMessage.CommunicationType.ECS_GetNotification);
                        KVMessage message = helper_communicator.receiveKVMessage();

                        if (message.getValue() != null && message.getCommunicationType() == KVMessage.CommunicationType.ECS_GetNotification) {
                           logger.info("GET NOTIFICATION VALUE ---"+message.getValue());
                        }

                    } catch (IOException ioe) {
                        logger.error("Helper_thread send error");
                        return;
                    }

                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        System.out.println("Helper_thread sleep error");
                    }


                }

                if(type==1)
                    System.out.println("WATCH being closed");


            }

            try {
                if (helper_ClientSocket != null) {
                    helper_input.close();
                    helper_output.close();
                    helper_communicator.closeCommunication();
                    helper_ClientSocket.close();
                    helper_ClientSocket = null;
                }
            } catch (IOException ioe) {
                System.out.println("Helper tearDown error");
            }

        }
    }

    @Override
    public void connect() throws Exception {
		//logger.info(ServerAddress + portNumber);
        ClientSocket = new Socket(this.ServerAddress, this.portNumber);

        try {
            output = ClientSocket.getOutputStream();
            input = ClientSocket.getInputStream();
            communicator = new KVCommunication(ClientSocket, null);
            logger.debug("KV Store successfully connected to server address: " + ServerAddress + " port number: " + portNumber);
        } catch (Exception a) {
            logger.error(a + "Unable to connect! Please check the server address and port number!");
        }

        if (isClient) {
            stopped = false;
            KVStore_Helper kv_helper;
            if(Helper_key==null) kv_helper=this.new KVStore_Helper(this.ServerAddress, this.portNumber,this);
            else
               kv_helper = this.new KVStore_Helper(this.ServerAddress, this.portNumber,Helper_key,this);
            this.Helper_key=null;
            Thread thread = new Thread(kv_helper);
            thread.start();
        }
    }


    @Override
    public void disconnect() {
        logger.debug("try to close connection ...");
        try {
            stopped = true;
            //thread.interrupt();
            tearDownConnection();
        } catch (IOException ioe) {
            logger.error("Unable to close connection!");
        }

    }

    private void tearDownConnection() throws IOException {
        logger.debug("tearing down the connection ...");
        if (ClientSocket != null) {
            input.close();
            output.close();
            communicator.closeCommunication();
            ClientSocket.close();
            ClientSocket = null;
            logger.debug("connection closed!");
        }
    }

    private KVMessage reconnect(String key, String value, KVMessage.StatusType Type) {
        KVMessage message = null;
        disconnect();
        if (replicaOneAdd != null) {
            this.ServerAddress = replicaOneAdd;
            this.portNumber = replicaOnePort;

            try {
                connect();

                //connectIfLostConnection();
                communicator.sendKVMessage(Type, key, value);
                message = communicator.receiveKVMessage();
                message = reconnectServer(message, Type, key, value);
                return message;
            } catch (Exception e) {
                System.out.println("Should not be here");
            }
        }
        return message;
    }

    @Override
    public KVMessage put(String key, String value) throws Exception {
        try {
            connectIfLostConnection();
            communicator.sendKVMessage(StatusType.PUT, key, value);
            KVMessage message = communicator.receiveKVMessage();
            message = reconnectServer(message, StatusType.PUT, key, value);
            if (message.getStatus() == StatusType.SERVER_DISCONNECTED) {
                message = reconnect(key, value, StatusType.PUT);
            }
            return message;
        } catch (Exception e) {
            return reconnect(key, value, StatusType.PUT);
        }
    }

    @Override
    public KVMessage get(String key) throws Exception {
        try {
            connectIfLostConnection();
            communicator.sendKVMessage(StatusType.GET, key, null);
            KVMessage message = communicator.receiveKVMessage();
            message = reconnectServer(message, StatusType.GET, key, null);
            if (message.getStatus() == StatusType.SERVER_DISCONNECTED) {
                message = reconnect(key, null, StatusType.GET);
            }
            return message;
        } catch (Exception e) {
            return reconnect(key, null, StatusType.GET);
        }
    }

    //Used for ServerTransfer
    public KVMessage moveData(String value) throws Exception {
        communicator.sendKVMessage(StatusType.GET, "temp", value, KVMessage.CommunicationType.ECS_ReceiveData);
        return communicator.receiveKVMessage();
    }

    public KVMessage notifyReplica(int port, String host) throws Exception{
        communicator.sendKVMessage(StatusType.GET, Integer.toString(port), host, KVMessage.CommunicationType.ECS_ConnectReplica);
        return communicator.receiveKVMessage();
    }

    public KVMessage sendCommunicationMessage(String key, String value, KVMessage.CommunicationType type) throws Exception{
        communicator.sendKVMessage(StatusType.GET, key, value, type);
        return communicator.receiveKVMessage();
    }


    @Override
    public boolean isConnected() {
        try{
            return ClientSocket != null && ClientSocket.getInetAddress().isReachable(10);
        }
        catch (IOException ioe){
            return false;
        }
    }

    public KVMessage notifyServerSideCloseChannel() throws  Exception{
        return sendCommunicationMessage("close", "close", KVMessage.CommunicationType.ECS_ServerClose);
    }

    private void findResponsibleServer(String key){
        try {
            if (metaData != null) {
                BigInteger hashValue = MD5.HashInBI(key);
                for (Map.Entry<BigInteger, MetaData> entry : metaData.entrySet()) {
                    MetaData temp = entry.getValue();
                    String[] range = temp.getHashRange();
                    BigInteger upper = new BigInteger(range[0]);
                    BigInteger lower = new BigInteger(range[1]);
                    boolean descend = upper.compareTo(lower) == 1;
                    if (hashValue.compareTo(upper) == 0 ||
                            hashValue.compareTo(lower) == 0 ||
                            (hashValue.compareTo(upper) == 1 && hashValue.compareTo(lower) == -1 && !descend) ||
                            (hashValue.compareTo(upper) == -1 && hashValue.compareTo(lower) == -1 && descend) ||
                            (hashValue.compareTo(upper) == 1 && hashValue.compareTo(lower) == 1 && descend)) {
                        // find the corresponding range
                        this.portNumber = temp.getPort();
                        this.ServerAddress = temp.getHost();
                        break;
                    }
                }
            } else {
                logger.error("Could not obtain the recent metadata from server");
            }
        } catch (NoSuchAlgorithmException nsae){
            logger.error("Find responsible server " + nsae);
        }
    }

    private KVMessage reconnectServer(KVMessage oldMessage, StatusType status, String key, String value) throws  Exception{
        KVMessage message = oldMessage;
        while (message.getStatus() == StatusType.SERVER_NOT_RESPONSIBLE) {
            TreeMap<BigInteger, MetaData> newMetaData = message.getMetaData();
            metaData = newMetaData;
            disconnect();
            // find the corresponding one and connect
            // send and receive message again
            findResponsibleServer(key);
            connect();
            communicator.sendKVMessage(status, key, value);
            message = communicator.receiveKVMessage();
        }

        return message;
    }

    private void connectIfLostConnection(){
        try {
             while (!isConnected()) {
                for (MetaData data : metaData.values()) {
                    if (data.getPort() != this.portNumber) {
                        this.portNumber = data.getPort();
                        this.ServerAddress = data.getHost();
                        disconnect();
                        connect();
                    }
                }
            }
        } catch (Exception e){
            logger.error("Could not connect to server");
        }
    }

    public int getPortNumber(){
        return portNumber;
    }

    public String getReplicaOneAdd() { return replicaOneAdd; }

    public int getReplicaOnePort() { return replicaOnePort; }
}
