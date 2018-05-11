package client;

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

    private TreeMap<BigInteger, MetaData> metaData;

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

    @Override
    public void connect() throws Exception {
		//logger.info(ServerAddress + portNumber);
        ClientSocket = new Socket(ServerAddress, portNumber);
        try {
            output = ClientSocket.getOutputStream();
            input = ClientSocket.getInputStream();
            communicator = new KVCommunication(ClientSocket, null);
            logger.debug("KV Store successfully connected to server address: " + ServerAddress + " port number: " + portNumber);
        } catch (Exception a) {
            logger.error(a + "Unable to connect! Please check the server address and port number!");
        }
    }


    @Override
    public void disconnect() {
        logger.debug("try to close connection ...");
        try {
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
            ClientSocket.close();
            ClientSocket = null;
            logger.debug("connection closed!");
        }
    }

    @Override
    public KVMessage put(String key, String value) throws Exception {
        connectIfLostConnection();
        communicator.sendKVMessage(StatusType.PUT, key, value);
        KVMessage message = communicator.receiveKVMessage();
        message = reconnectServer(message, StatusType.PUT, key, value);
        return message;
    }

    @Override
    public KVMessage get(String key) throws Exception {
        connectIfLostConnection();
        communicator.sendKVMessage(StatusType.GET, key, null);
        KVMessage message =  communicator.receiveKVMessage();
        message = reconnectServer(message, StatusType.GET, key, null);
        return message;
    }

    //Used for ServerTransfer
    public KVMessage moveData(String value) throws Exception {
        communicator.sendKVMessage(StatusType.GET, "temp", value, KVMessage.CommunicationType.ECS_ReceiveData);
        return communicator.receiveKVMessage();
    }


    @Override
    public boolean isConnected() {
        try{
            if (ClientSocket != null && ClientSocket.getInetAddress().isReachable(10))
                return true;
            else
                return false;
        }
        catch (IOException ioe){
            return false;
        }
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
}
