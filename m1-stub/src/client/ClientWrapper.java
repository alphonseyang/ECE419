package client;

import client.KVStore;
import common.messages.KVMessage;

import java.util.Random;


public class ClientWrapper implements Runnable{

    private String ClientId;

    public KVStore client;

    public boolean Fail = false;
    private int TH;

    public enum TestType {
        PutGetDifferentKey, 			/* Get and puts */
        PutSameKey,            /* Multiple puts*/
        UpdateGetSameKey             /*Multiple Gets*/
    }
    TestType testtype;

    /**
     * This is a client that repeatedly puts a kvp
     * */
    public ClientWrapper(String address, int port,String clientID, int th,TestType type){
        this.ClientId = clientID;
        this.TH = th;
        this.testtype=type;

        try{
            this.client=new KVStore(address, port);
            this.client.connect();
        } catch (Exception ex)
        {}
    }
    public void UpdateGetSameKey(){
        int i=0;
        while (i<TH){
            String randomKey = "keyy"+i;// + this.id;
            String randomvariable = new Random().nextInt(TH) + "";
            try {
                KVMessage result = this.client.put(randomKey, randomvariable + this.ClientId);
                result = this.client.put(randomKey, randomvariable + this.ClientId);
                String t = randomvariable + this.ClientId;
                if ((result.getStatus() == KVMessage.StatusType.PUT_UPDATE) && (result.getValue().equals(t))) {
                } else {
                    Fail = true;
                    System.out.println("Thread " + ClientId + result.getStatus() + " value " + result.getValue() + " key:" + result.getKey());
                    System.out.println("Key is " + randomKey + " value " + t);
                    throw new Exception("Thread"+this.ClientId+"wrong");
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                break;
            }
        i++;
        }
        this.client.disconnect();

    }
    public void PutSameKey(){
        int i=0;
        while(i<TH) {
            String randomKey = "keyy"+i;// + this.id;
            String randomvariable = new Random().nextInt(TH) + "";
            try {
                KVMessage result = this.client.put(randomKey, randomvariable + this.ClientId);
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                break;
            }
            i++;
        }

        this.client.disconnect();
    }
    public void PutGetDifferentKey(){
        int i=0;
        while(i<TH) {
            String randomKey = "" + new Random().nextInt(TH) + this.ClientId;
            String randomvariable = new Random().nextInt(TH) + "";
            try {
                this.client.put(randomKey, randomvariable + this.ClientId);
                KVMessage result = this.client.get(randomKey);
                String t = randomvariable + this.ClientId;
                if ((result.getStatus() == KVMessage.StatusType.GET_SUCCESS) && (result.getValue().equals(t))) {
                } else {
                    Fail = true;
                    System.out.println("Thread " + ClientId + result.getStatus() + " value " + result.getValue() + " key:" + result.getKey());
                    System.out.println("Key is " + randomKey + " value " + t);
                    throw new Exception("Thread"+this.ClientId+"wrong");
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                break;
            }
            i++;
        }
        this.client.disconnect();

    }
    public void run(){
        switch (this.testtype){
            case PutGetDifferentKey:
                PutGetDifferentKey();
            case PutSameKey:
                PutSameKey();
            case UpdateGetSameKey:
                UpdateGetSameKey();
            default:
        }

    }
}