package common.communication;

import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.util.TreeMap;
import java.util.Iterator;

import common.datatypes.MetaData;
import common.KVMessage;
import common.KVMessage.StatusType;
import common.KVMessageClass;

import common.KVMessage.CommunicationType;

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
        } catch (IOException ioe) {
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
                        if (message.getCommunicationType() == null)
                            sendKVMessage(sent.getStatus(), sent.getKey(), sent.getValue());
                        else
                            sendKVMessage(sent.getStatus(), sent.getKey(), sent.getValue(), sent.getCommunicationType());
                    } catch (IOException ioe) {
                        logger.error("Error! Server lost client lost! " + ioe);
                        isOpen = false;
                    } catch (Exception e) {
                        logger.error(e);
                    }
                }
            }

        } finally {
            try {
                logger.debug("Close communicator channel");
                closeCommunication();
            } catch (IOException ioe) {
                logger.error("Error! Unable to tear down connection!", ioe);
            }
        }
    }

    public KVMessage sendKVMessage(StatusType status, String key, String value) throws IOException {
        KVMessage Tmp = new KVMessageClass(status, key, value);
        logger.debug("KV Communication Sent Message: Status is " + Tmp.getStatus() + "; KEY is " + Tmp.getKey() + "; Value is " + Tmp.getValue());

        // JSON transfering way
        if (status == StatusType.SERVER_NOT_RESPONSIBLE){
            Tmp.setMetaData(kvServer.getMetaData());
        }
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
    public KVMessage sendKVMessage(StatusType status, String key, String value,CommunicationType Communicationtype) throws IOException {
        KVMessage Tmp;
        if(Communicationtype!=null)
            Tmp = new KVMessageClass(status, key, value,Communicationtype);
        else
            Tmp = new KVMessageClass(status, key, value);
        logger.debug("KV Communication Sent Message: Status is " + Tmp.getStatus() + "; KEY is " + Tmp.getKey() + "; Value is " + Tmp.getValue()+"; CommunicationType is "+Communicationtype.toString());

        // JSON transfering way
        if (status == StatusType.SERVER_NOT_RESPONSIBLE){
            Tmp.setMetaData(kvServer.getMetaData());
        }
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
            String communicationType=null;

            TreeMap<BigInteger, MetaData> metaData = null;
            if (jo.has("key")) key = jo.getString("key");
            if (jo.has("value")) value = jo.getString("value");
            if (jo.has("status")) status = jo.getString("status");
            if (status.equals(StatusType.SERVER_NOT_RESPONSIBLE.name()) && jo.has("metaData"))
                metaData = readMetaDataFromJSON(jo.getJSONObject("metaData"));
            if (jo.has("CommType")) communicationType = jo.getString("CommType");
            //jo.put("CommType", message.getCommunicationType());

            if (communicationType == null)
                message = new KVMessageClass(StatusType.valueOf(status), key, value,null);
            else
                message = new KVMessageClass(StatusType.valueOf(status), key, value,CommunicationType.valueOf(communicationType));
            message.setMetaData(metaData);
            logger.debug("KV Communication Received Message: Status is " + message.getStatus() + "; KEY is " + message.getKey() + "; Value is " + message.getValue());
        } catch (JSONException je) {
            logger.error("Error! Read object failed! " + je);
        }

        return message;
    }

    public void closeCommunication() throws IOException {
        if (clientSocket != null) {
            input.close();
            output.close();
            clientSocket.close();
        }
    }

    private KVMessage processKVMessage(KVMessage message) throws Exception {
        KVMessage result = null;

        try {
            if (message != null) {
                String key = message.getKey();
                String value = message.getValue();
                StatusType status = message.getStatus();
                StatusType resultStatus = message.getStatus();
                CommunicationType CommunicaionType=message.getCommunicationType();
                if (!kvServer.isOldVersion() && kvServer.getServerState() == KVServer.ServerStateType.STOPPED && CommunicaionType == null) {
                    // server is in stopped state, return server_stopped error message
                    result = new KVMessageClass(StatusType.SERVER_STOPPED, key, value);
                    //get the responsible node and send store the
                    //result.setResponsibleNode();
                }
                else if (CommunicaionType != null)
                {
                    //ECS or Server to Server operation
                    switch (CommunicaionType)
                    {
                        case ECS_MoveData:
                        {
                            //Call send data and send an message to ECS when operation complete
                            //Release the write lock on the successor server and
                            // finally remove the data items that are no longer handled by this server

                            break;
                        }
                        case ECS_ReceiveData:
                        {
                            kvServer.ReceivedData(value);
                            result = new KVMessageClass(StatusType.GET, key, "",CommunicationType.ECS_ReceiveDataFinished);
                            break;
                        }
                        case ECS_Default:
                            break;

                        default:
                            break;

                    }

                }

                else {
                    if (!kvServer.isOldVersion() && !isResponsibleServer(key) ) {
                        // if it is not responsible server
                        result = new KVMessageClass(StatusType.SERVER_NOT_RESPONSIBLE, key, value);
                    } else {
                        switch (status) {
                            case PUT:
                                if (!kvServer.isOldVersion() && kvServer.isWriterLocked()) {
                                    // server is locked, return server_write_lock message
                                    result = new KVMessageClass(StatusType.SERVER_WRITE_LOCK, key, value);
                                } else {
                                    if ((key.contains(" ") && value != null) || key.equals("") || key.length() < 1 || key.length() > 20 || (value != null && value.length() > 122880)) {
                                        result = new KVMessageClass(StatusType.PUT_ERROR, key, value);
                                    } else if (key.contains(" ") && value == null) {
                                        result = new KVMessageClass(StatusType.DELETE_ERROR, key, value);
                                    } else {
                                        if (value != null && !value.equals("null") && !value.equals("")) {
                                            if (kvServer.inCache(key) || kvServer.inStorage(key))
                                                resultStatus = StatusType.PUT_UPDATE;
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
                                }
                                break;
                            case GET:
                                if (key.contains(" ") || key.length() > 20 || key.length() < 1) {
                                    result = new KVMessageClass(StatusType.GET_ERROR, key, value);
                                } else {
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
                }
            }
        } catch (Exception e) {
            //deal with the exception of put error
            result = new KVMessageClass(StatusType.PUT_ERROR, null, null);
        }

        return result;
    }

    private static JSONObject messageToJSONObject(KVMessage message) {
        JSONObject jo = new JSONObject();
        try {
            jo.put("key", message.getKey());
            jo.put("value", message.getValue());
            jo.put("status", message.getStatus());
            jo.put("metaData", message.getMetaData());
            if(message.getCommunicationType()!=null)
                jo.put("CommType", message.getCommunicationType());
        } catch (JSONException je) {
            logger.error("Error! JSON Exception " + je);
        }

        return jo;
    }

    private boolean isResponsibleServer(String key) {
        return kvServer.isResponsibleServer(key);
    }

    private TreeMap<BigInteger, MetaData> readMetaDataFromJSON(JSONObject rawData){
        TreeMap<BigInteger, MetaData> map = new TreeMap<>();
        Iterator<String> keys = rawData.keys();
        try {
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject value = rawData.getJSONObject(key);
                MetaData dataInstance = new MetaData(value.getString("name"),
                                                     value.getString("host"),
                                                     value.getInt("port"),
                                                     value.getJSONArray("hashRange").getString(0),
                                                     value.getJSONArray("hashRange").getString(1));
                map.put(new BigInteger(key), dataInstance);
            }
        } catch (JSONException je){
            logger.error("Communicator read metadata error " + je);
        }

        return map;
    }


}
