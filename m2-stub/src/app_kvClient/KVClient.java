package app_kvClient;

import client.KVCommInterface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.UnknownHostException;

import logger.LogSetup;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import client.KVStore;
import common.KVMessage;
////TEST PURPOSE!!!!
import  java.io.RandomAccessFile;
import java.lang.Exception;

public class KVClient implements IKVClient {
    private static Logger logger = Logger.getRootLogger();
    private boolean stop = false; // Flag to indicate stop or not
    private BufferedReader stdin;
    private static final String PROMPT = "M2-Client> ";
    private String serverAddress;
    private int serverPort;

    private KVStore store = null;

    public static void main(String[] args) {

        try {
            new LogSetup("logs/client.log", Level.INFO);
            KVClient client = new KVClient();
            client.run();
        } catch (IOException e) {
            System.out.println("Unable to initialize logger!");
            e.printStackTrace();
            System.exit(1);
        }

    }

    @Override
    public void newConnection(String hostname, int port) throws UnknownHostException, Exception {
        store = new KVStore(hostname, port);
        store.connect();
    }

    @Override
    public KVCommInterface getStore() {
        return store;
    }

    public void handleCommand(String cmdLine) {
        String[] tokens = cmdLine.split("\\s+");
        if (tokens[0].equals("quit")) {
            stop = true;
            if (store != null) {
                store.disconnect();
                store = null;
            }
            logger.info(PROMPT + "Application exit!");

        } else if (tokens[0].equals("connect")) {
            if (tokens.length == 3) {
                try {
                    serverAddress = tokens[1];
                    serverPort = Integer.parseInt(tokens[2]);
                    newConnection(serverAddress, serverPort);
                    logger.info("KV Client Successfully connected to server address: " + serverAddress + " port number: " + serverPort);
                    // store.receiveMessage();
                } catch (NumberFormatException nfe) {
                    printError("No valid address. Port must be a number!");
                    // logger.info("Unable to parse argument <port>", nfe);
                } catch (UnknownHostException e) {
                    printError("Unknown Host!");
                    // logger.info("Unknown Host!", e);
                } catch (Exception e) {
                    printError("Could not establish connection!");
                    // logger.warn("Could not establish connection!", e);
                }
            } else {
                printError("Invalid number of parameters!");
            }

        } else if (tokens[0].equals("put")) {
            if (tokens.length >= 2) {
                if (store != null && store.isConnected()) {
                    String key = tokens[1];
                    if (key.length() <= 20 && key.length() > 0)
                    {
                        String value = convertToValue(tokens);
                        if (value == null || ( value != null && value.length() <= 122880)){
                            try {
                                KVMessage temp = store.put(key, value);
                                logger.info("Status: "+temp.getStatus() + " Key: "+temp.getKey()+" Value: "+temp.getValue());
                            } catch (Exception e) {
                                logger.error("Put message error "+e);
                            }
                        }
                    }
                    else {
                        printError("Key length should be between 1 and 20!");
                    }

                } else {
                    printError("Not connected");
                }
            } else {
                printError("Invalid number of parameters!");
            }
        } else if (tokens[0].equals("get")) {
            if (tokens.length == 2) {
                if (store != null && store.isConnected()) {
                    String key = tokens[1];
                    try {
                        KVMessage temp = store.get(key);
                        logger.info("Status: "+temp.getStatus() + " Key: "+temp.getKey()+" Value: "+temp.getValue());
                    } catch (Exception e) {
                        logger.error("Get message error "+e);
                    }
                } else {
                    printError("Not connected");
                }
            } else {
                printError("Invalid number of parameters!");
            }
        }
        else if (tokens[0].equals("disconnect")) {
            if (tokens.length == 1){
                store.disconnect();
                logger.info("KV Client successfully disconnected");
            } else {
                printError("Invalid number of parameters!");
            }
        }
        else if(tokens[0].equals("help")) {
            if (tokens.length == 1){
                printHelp();
            } else {
                printError("Invalid number of parameters!");
            }
        }
        else if(tokens[0].equals("transfer")) {
            try {

                RandomAccessFile raf = new RandomAccessFile("Server20000.txt", "r");

                byte[] b = new byte[(int) raf.length()];
                raf.readFully(b);
                System.out.println("size of file is "+b.length);
                String str = new String(b, "UTF-8"); // for UTF-8 encoding
                System.out.println("size of file is "+str.length());

                //store.moveData(str);
                raf.close();

            } catch (IOException e) {
                ;
            }
            catch (Exception e)
            {
                ;
            }
        }
        else if (tokens[0].equals("debug")){
            logger.setLevel(Level.DEBUG);
            logger.info("Logger level set to debug");
        }
        else if (tokens[0].equals("info")){
            logger.setLevel(Level.INFO);
            logger.info("Logger level set to info");
        }
        else {
            printError("Wrong command!");
        }
    }

    private void printError(String error) {
        logger.error(PROMPT + "Error! " + error);
    }

    private String convertToValue(String[] tokens){
        // in the case that there's no value, so it is a delete command
        if (tokens.length == 2) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < tokens.length-1; i++){
            sb.append(tokens[i]);
        }
        sb.append(tokens[tokens.length-1]);
        return sb.toString();
    }

    public void run() {
        while (!stop) {
            stdin = new BufferedReader(new InputStreamReader(System.in));
            System.out.print(PROMPT);

            try {
                String cmdLine = stdin.readLine();
                this.handleCommand(cmdLine);
            } catch (IOException e) {
                stop = true;

                printError("CLI does not respond - Appilication terminated ");
            }

        }
    }

    private void printHelp(){
        logger.info("connect <ServerAddress> <PortNumber> ; " +
                "put <key> <value>; " +
                "get <key>; disconnect; " +
                "help;" +
                "debug" +
                "info");
    }

}
