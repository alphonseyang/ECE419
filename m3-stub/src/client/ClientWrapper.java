package client;

import common.KVMessage;

import java.util.HashMap;
import java.util.Random;


public class ClientWrapper implements Runnable{

    private String ClientId;

    public KVStore client;

    public boolean Fail = false;
    private int TH;
    private HashMap<String,String>data=null;

    public enum TestType {
        PutGetDifferentKey, 			/* Get and puts */
        PutSameKey,            /* Multiple puts*/
        UpdateGetSameKey,             /*Multiple Gets*/
        MoveDataTest,
        PerformanceTest

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
    public ClientWrapper(String address, int port, String clientID, int th, TestType type, HashMap<String,String>da_ta){
        this.ClientId = clientID;
        this.TH = th;
        this.testtype=type;
        this.data=da_ta;

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
    public void MoveDataTest(){
        String randomKey="key"+this.ClientId;
        String randomvariable = "value"+this.ClientId;
        try{
            this.client.put(randomKey, randomvariable);
            KVMessage result = this.client.get(randomKey);
            String t = randomvariable;
            if ((result.getStatus() == KVMessage.StatusType.GET_SUCCESS) && (result.getValue().equals(t))) {
            } else {
                Fail = true;
                System.out.println("Thread " + ClientId + result.getStatus() + " value " + result.getValue() + " key:" + result.getKey());
                System.out.println("Key is " + randomKey + " value " + t);
                throw new Exception("Thread"+this.ClientId+"wrong");
            }
        } catch (Exception ex)
        {
            System.out.println(ex.getMessage());

        }

    }
    public void PerformanceTest(){
        //Measure the get time
        int countbyte=0;
        final long newstartTime = System.nanoTime();
        for (String key : data.keySet()) {

            try {
                KVMessage temp = client.get(key);
                countbyte+=data.get(key).length();
                if(temp.getValue().equals(data.get(key))){}
                else
                    Fail=true;

            } catch (Exception e) {
               Fail= true;
            }
        }
         client.disconnect();
        int size=data.size();

        final long newduration = System.nanoTime() - newstartTime;
        long newresult = newduration;
        float averageread = (float) ((newduration / size) / Math.pow(10, 6));
        System.out.println("Thread "+ClientId+": Average Read Time per request " + String.format("%.2f", averageread) + " ms/request");
        //System.out.println("Average Read ThroughPut"+String.valueOf(1/(newresult/newsize)));
        float readThroughput = (float) (((countbyte * (Math.pow(10, 6))) / newresult));

        System.out.println("Thread "+ClientId+": Read throughtput is " + String.format("%.2f", readThroughput) + "KB per Second");



    }

    public void run(){
        switch (this.testtype){
            case PutGetDifferentKey:
                PutGetDifferentKey();
                break;
            case PutSameKey:
                PutSameKey();
                break;
            case UpdateGetSameKey:
                UpdateGetSameKey();
                break;

            case MoveDataTest:
                 MoveDataTest();
            case PerformanceTest:
                 PerformanceTest();
            default:
        }

    }
}