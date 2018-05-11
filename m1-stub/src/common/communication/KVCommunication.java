package common.communication;

import java.io.*;
import java.net.Socket;

import common.messages.KVMessage;
import common.messages.KVMessage.StatusType;
import common.messages.KVMessageClass;

import org.apache.log4j.Logger;
import org.json.*;

import java.io.IOException;

import app_kvServer.KVServer;

import java.io.DataInputStream;
import java.io.DataOutputStream;

public class KVCommunication implements Runnable {

    private static Logger logger = Logger.getRootLogger();

    private boolean isOpen;
    private static final int BUFFER_SIZE = 1024;

    private KVServer kvServer;
    private Socket clientSocket;
    private InputStream input;
    private OutputStream output;

    /**
     * Constructs a new CientConnection object for a given TCP socket.
     *
     * @param clientSocket the Socket object for the client connection.
     */
    public KVCommunication(Socket clientSocket, KVServer server) {
        this.clientSocket = clientSocket;
        this.kvServer = server;
        this.isOpen = true;
        try {
            this.output = clientSocket.getOutputStream();
            this.input = clientSocket.getInputStream();
        }
        catch (IOException ioe) {
            logger.error("Error! Connection could not be established!", ioe);

        }
    }

    /**
     * Initializes and starts the client connection.
     * Loops until the connection is closed or aborted by the client.
     */
    public void run() {
        try {
            if (kvServer != null) {
                while (isOpen) {
                    try {
                        KVMessage message = receiveKVMessage();
                        KVMessage sent = processKVMessage(message);
                        sendKVMessage(sent.getStatus(), sent.getKey(), sent.getValue());
                    } catch (IOException ioe) {
                        logger.error("Error! Server lost client lost! " + ioe);
                        isOpen = false;
                    } catch (Exception e) {
						logger.error(e);
					}
                }
            }

        }
        finally {

            try {
                if (clientSocket != null) {
                    input.close();
                    output.close();
                    clientSocket.close();
                }
            } catch (IOException ioe) {
                logger.error("Error! Unable to tear down connection!", ioe);
            }
        }
    }

    public KVMessage sendKVMessage(StatusType status, String key, String value) throws IOException {
        KVMessage Tmp = new KVMessageClass(status, key, value);
        logger.info("Sent Message: Status is " + Tmp.getStatus() + "; KEY is " + Tmp.getKey() + "; Value is " + Tmp.getValue());
        
        // JSON transfering way
        JSONObject job = messageToJSONObject(Tmp);
        byte[] data = job.toString().getBytes("UTF-8");

        // send bytes array length
        int dataLength = data.length;
        DataOutputStream dos = new DataOutputStream(output);
        dos.writeInt(dataLength);
        dos.flush();

        //Send to server
        output.write(data, 0, data.length);
        output.flush();

        return Tmp;
    }

    public KVMessage receiveKVMessage() throws IOException {
        DataInputStream dis = new DataInputStream(input);
        int length = dis.readInt();

        int index = 0;
        byte[] msgBytes = null, tmp = null;
        byte[] bufferBytes = new byte[BUFFER_SIZE];

        for (int i = 0; i < length; i++) {
            byte read = (byte) input.read();

            if (index == BUFFER_SIZE) {
                if (msgBytes == null) {
                    tmp = new byte[BUFFER_SIZE];
                    System.arraycopy(bufferBytes, 0, tmp, 0, BUFFER_SIZE);
                } else {
                    tmp = new byte[msgBytes.length + BUFFER_SIZE];
                    System.arraycopy(msgBytes, 0, tmp, 0, msgBytes.length);
                    System.arraycopy(bufferBytes, 0, tmp, msgBytes.length,
                            BUFFER_SIZE);
                }

                msgBytes = tmp;
                bufferBytes = new byte[BUFFER_SIZE];
                index = 0;
            }

            /* only read valid characters, i.e. letters and constants */
            bufferBytes[index] = read;
            index++;
        }

        if (msgBytes == null) {
            tmp = new byte[index];
            System.arraycopy(bufferBytes, 0, tmp, 0, index);
        } else {
            tmp = new byte[msgBytes.length + index];
            System.arraycopy(msgBytes, 0, tmp, 0, msgBytes.length);
            System.arraycopy(bufferBytes, 0, tmp, msgBytes.length, index);
        }

        msgBytes = tmp;
        KVMessage message = null;
        JSONObject jo = null;

        try {
            jo = new JSONObject(new String(msgBytes));
            String key = null;
            String value = null;
            String status = null;
            if (jo.has("key")) key = jo.getString("key");
            if (jo.has("value")) value = jo.getString("value");
            if (jo.has("status")) status = jo.getString("status");

            message = new KVMessageClass(stringToStatus(status), key, value);
            logger.info("Received Message: Status is " + message.getStatus() + "; KEY is " + message.getKey() + "; Value is " + message.getValue());
        } catch (JSONException je) {
            logger.error("Error! Read object failed! " + je);
        }

        return message;
    }

    public void closeCommunication(){
        try {
            if (clientSocket != null) {
                input.close();
                output.close();
                clientSocket.close();
            }
        }catch (IOException ioe) {
            logger.error("Error! Unable to tear down connection!", ioe);
        }
    }
    
    private KVMessage processKVMessage(KVMessage message) throws Exception {
    	KVMessage result = null;
    	
    	try{
    		if (message != null){
    			String key = message.getKey();
				String value = message.getValue();
				StatusType status = message.getStatus();
				StatusType resultStatus = message.getStatus();
    			switch (status){
    				case PUT:
    				    if (key.contains(" ") || key.equals("") || key.length() < 1 || key.length() > 20 || (value != null && value.length() > 122880)) {
                            result = new KVMessageClass(StatusType.PUT_ERROR, key, value);
                        }
                        else {
                            if (value != null && !value.equals("null") && !value.equals(""))  {
                                if (kvServer.inCache(key) || kvServer.inStorage(key)) resultStatus = StatusType.PUT_UPDATE;
                                else resultStatus = StatusType.PUT_SUCCESS;
                                kvServer.putKV(key, value);
                                result = new KVMessageClass(resultStatus, key, value);
                            } else {
                                //delete the key value pair
                                if (kvServer.inCache(key) || kvServer.inStorage(key)) {   //resultStatus = StatusType.PUT_UPDATE;
                                    kvServer.deleteKV(key);
                                    resultStatus = StatusType.DELETE_SUCCESS;
                                } else {
                                    resultStatus = StatusType.DELETE_ERROR;
                                }
                                result = new KVMessageClass(resultStatus, key, value);
                            }
                        }
    					break;
    				case GET:
                        if (key.length() > 20 || key.length() < 1 ) {
                            result = new KVMessageClass(StatusType.GET_ERROR, key, value);
                        }
                        else {
                            String valGot = kvServer.getKV(key);
                            if (valGot != null) resultStatus = StatusType.GET_SUCCESS;
                            else resultStatus = StatusType.GET_ERROR;
                            result = new KVMessageClass(resultStatus, key, valGot);
                        }
    					break;
    				default:
    					break;
    			}
    		}
    	} catch (Exception e) {
    		//deal with the exception of put error
			result = new KVMessageClass(StatusType.PUT_ERROR, null, null);
		}

		return result;
	}

    private static JSONObject messageToJSONObject(KVMessage message){
        JSONObject jo = new JSONObject();
        try{
            jo.put("key", message.getKey());
            jo.put("value", message.getValue());
            jo.put("status", message.getStatus());
        } catch (JSONException je){
            logger.error("Error! JSON Exception " + je);
        }

        return jo;
    }

    private StatusType stringToStatus(String str){
        switch (str){
            case "PUT":
                return StatusType.PUT;
            case "PUT_SUCCESS":
                return StatusType.PUT_SUCCESS;
            case "PUT_UPDATE":
                return StatusType.PUT_UPDATE;
            case "PUT_ERROR":
                return StatusType.PUT_ERROR;
            case "GET":
                return StatusType.GET;
            case "GET_ERROR":
                return StatusType.GET_ERROR;
            case "GET_SUCCESS":
                return StatusType.GET_SUCCESS;
            case "DELETE_SUCCESS":
                return StatusType.DELETE_SUCCESS;
            case "DELETE_ERROR":
                return StatusType.DELETE_ERROR;
            default:
                return null;
        }
    }
}
