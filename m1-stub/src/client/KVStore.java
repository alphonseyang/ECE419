package client;

import common.messages.KVMessage;
import common.messages.KVMessage.StatusType;
import common.communication.KVCommunication;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.Socket;

import org.apache.log4j.Logger;


public class KVStore implements KVCommInterface {

    private static Logger logger = Logger.getRootLogger();
    private Socket ClientSocket;
    private String ServerAddress;
    private int portNumber;
    private OutputStream output;
    private InputStream input;

    private KVCommunication communicator;


    /**
     * Initialize KVStore with address and port of KVServer
     *
     * @param address the address of the KVServer
     * @param port    the port of the KVServer
     */
    public KVStore(String address, int port) {
        ServerAddress = address;
        portNumber = port;
        logger.info("KVStore Initialized");
    }

    @Override
    public void connect() throws Exception {
    	//System.out.println("connect");
        ClientSocket = new Socket(ServerAddress, portNumber);
        try {
            output = ClientSocket.getOutputStream();
            input = ClientSocket.getInputStream();
            communicator = new KVCommunication(ClientSocket, null);
            logger.info("Successfully connected to server address: " + ServerAddress + " port number: " + portNumber);
        } catch (Exception a) {
            logger.error(a + "Unable to connect! Please check the server address and port number!");
        }
    }


    @Override
    public void disconnect() {
        logger.info("try to close connection ...");

        try {
            tearDownConnection();
        } catch (IOException ioe) {
            logger.error("Unable to close connection!");
        }

    }

    private void tearDownConnection() throws IOException {
    	//System.out.println("disconnect");
        logger.info("tearing down the connection ...");
        if (ClientSocket != null) {
            input.close();
            output.close();
            ClientSocket.close();
            ClientSocket = null;
            logger.info("connection closed!");
        }
    }

    @Override
    public KVMessage put(String key, String value) throws Exception {
    	//System.out.println("store put");
        communicator.sendKVMessage(StatusType.PUT, key, value);
        return communicator.receiveKVMessage();
    }

    @Override
    public KVMessage get(String key) throws Exception {
        communicator.sendKVMessage(StatusType.GET, key, null);
        return communicator.receiveKVMessage();
    }

    public boolean isRunning() {
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
}