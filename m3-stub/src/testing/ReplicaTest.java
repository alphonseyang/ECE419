package testing;

import client.KVStore;
import common.KVMessage;
import ecs.IECSNode;
import org.apache.zookeeper.data.Stat;
import org.junit.Test;
import junit.framework.TestCase;

import app_kvECS.ECSClient;
import client.ClientWrapper;
import org.apache.zookeeper.*;

import java.io.IOException;
import java.math.BigInteger;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.Random;


public class ReplicaTest extends TestCase {
    private ECSClient ecsApp;
    private KVStore kvClient;
    private CountDownLatch connectedSignal = new CountDownLatch(1);
    private int numGetClients = 5;
    private String KILL_TEST = "lsof -i : %s | grep LISTEN | awk '{ print $2 }' | uniq | xargs kill -9 &";
    private HashMap<String,String>Database=getTestSamples();

    public void setUp() {
        try {
            ecsApp = new ECSClient("localhost", 2181, "ecs.config");
            ecsApp.addSSHName("localhost");
            //ecsApp.initiateStorageService(3, 10, "FIFO");
            ecsApp.setupNodes(3, "FIFO", 10);
            ecsApp.addNodes(3, "FIFO", 10);
            try {
                ecsApp.awaitNodes(3, 300000);
            } catch (Exception e) {

            }

            ecsApp.start();
            kvClient = null;
        } catch (Exception e) {
            System.out.println("ECS Test error " + e);
        }
    }

    public void tearDown() {
        ecsApp.shutdown();
    }

    public void setUpClientConnection() {
        TreeMap<BigInteger, IECSNode> node = ecsApp.getHashRing();
        String host = "";
        Integer PortNumber = 0;
        for (Map.Entry<BigInteger, IECSNode> entry : node.entrySet()) {
            IECSNode availableNode = entry.getValue();
            host = availableNode.getNodeHost();
            PortNumber = availableNode.getNodePort();
            if (availableNode.getFlag() == IECSNode.ECSNodeFlag.START) {
                break;
            }
        }
        kvClient = new KVStore(host, PortNumber);
    }
    public HashMap<String,String> getTestSamples(){
        HashMap<String, String> TestDataBase = new HashMap<String, String>();
        int a=0;
        for( a=1;a<50;a++) {
            String randomKey = new Random().nextInt(a) + "";
            Integer count = new Random().nextInt(a);
            String randomvariable = "va";
            for (int i = 0; i < count; i++) {
                randomvariable += i + "hellnnwehwehwqheqheqhehqwkjewjkejkewjkwejkewjkewkjje" +
                        "wkjewjkwejewjekwjejwekjwjkeoworldvalu" +
                        "e12341jfhsfhwheqehdawdwqqeewdjkrtjtjtkrk2shandadwqedadwadqweadqweadwa";
            }

            TestDataBase.put(randomKey,randomvariable);
        }


        return TestDataBase;
    }

    public ClientWrapper[] SetUpMultipleClientConnection(int NumberOfClients,HashMap<String,String>Database) {
        ClientWrapper MutlipleClients[] = new ClientWrapper[NumberOfClients];
        TreeMap<BigInteger, IECSNode> node = ecsApp.getHashRing();
        ArrayList<IECSNode> AvailableNodes=new ArrayList<IECSNode>();
        //String host = "";
        //Integer PortNumber = 0;
        int counter=0;
        for (Map.Entry<BigInteger, IECSNode> entry : node.entrySet()) {
            IECSNode availableNode = entry.getValue();
            // host = availableNode.getNodeHost();
            //PortNumber = availableNode.getNodePort();
            if (availableNode.getFlag() == IECSNode.ECSNodeFlag.START &&counter<NumberOfClients) {

                //counter++;
                AvailableNodes.add(availableNode);
            }
        }

        for(int i=0;i<NumberOfClients;i++)
        {
            int offset= i%AvailableNodes.size();
            String host=AvailableNodes.get(offset).getNodeHost();
            int PortNumber=AvailableNodes.get(offset).getNodePort();
            MutlipleClients[i] = new ClientWrapper(host, PortNumber, "" + i, 50, ClientWrapper.TestType.PerformanceTest,Database);
            //MutlipleClients[i] = new ClientWrapper("localhost", 50000+i, "" + i, 50, ClientWrapper.TestType.PerformanceTest,Database);
           // System.out.println("portNumber:"+PortNumber);
        }

        return MutlipleClients;

    }

    public ArrayList<IECSNode> GetAvailableNodes() {
        ArrayList<IECSNode> nodes = new ArrayList<>();

        TreeMap<BigInteger, IECSNode> node = ecsApp.getHashRing();
        String host = "";
        Integer PortNumber = 0;
        for (Map.Entry<BigInteger, IECSNode> entry : node.entrySet()) {
            IECSNode availableNode = entry.getValue();
            host = availableNode.getNodeHost();
            PortNumber = availableNode.getNodePort();
            if (availableNode.getFlag() == IECSNode.ECSNodeFlag.START) {
                nodes.add(availableNode);
            }
        }

        return nodes;
    }

    public void testconnect()
    {
        setUpClientConnection();
        KVMessage response = null;
        Exception ex = null;
        try {
            kvClient.connect();
        } catch (Exception e) {
            ex = e;
        }
        //Wait until server start
        try {
            while (true) {
                if (response != null && response.getStatus() != KVMessage.StatusType.SERVER_STOPPED)
                    break;
                ecsApp.start();
                response = kvClient.put("te", "123");
            }
            response = kvClient.get("te");
        } catch (Exception e) {
            ex = e;
        }
    }

    @Test
    public void testClientReconnection () {
        String key = "TestReconnect_Key";
        String value = "alligator";
        KVMessage response = null;
        Exception ex = null;

        KVStore Client = new KVStore("localhost", 50001, true);

        try {
            Client.connect();

            while (true) {
                if (response != null && response.getStatus() != KVMessage.StatusType.SERVER_STOPPED)
                    break;
                ecsApp.start();
                response = Client.put(key, value);
            }

            response = Client.get(key);
        } catch (Exception e) {
            ex = e;
        }

        assertTrue(ex == null && response.getValue().equals("alligator"));

        int port = Client.getPortNumber();
        ArrayList<String> nodeNames = new ArrayList<>();
        String name = "server" + Integer.toString((port-50000)+1) + ":127.0.0.1:" + Integer.toString(port);
        nodeNames.add(name);
        ecsApp.removeNodes(nodeNames);

        try {
            Thread.sleep(5000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        response = null;

        try {
            while (true) {
                if (response != null && response.getStatus() != KVMessage.StatusType.SERVER_STOPPED)
                    break;
                ecsApp.start();
                response = Client.get(key);
                //System.out.println(Client.getPortNumber() + "___" + Client.getReplicaOnePort());
            }
        } catch (Exception e) {
            ex = e;
        }

        assertTrue(ex == null && response.getValue().equals("alligator"));
    }

    @Test
    public void testReplicaPerformanceSingle()
    {
        setUpClientConnection();
        KVMessage response = null;
        Exception ex = null;
        testconnect();

        //Get test samples
        HashMap<String,String>Database=getTestSamples();
        int size=Database.size();
        final long startTime = System.nanoTime();
        int countbyte=0;
        for (String key : Database.keySet()) {
            try {
                KVMessage temp = kvClient.put(key, Database.get(key));
                countbyte+=Database.get(key).length();
            } catch (Exception e) {
                ex=e;
            }
        }
        //Measure the Writing time
        final long duration = System.nanoTime() - startTime;
        long result = duration;
        System.out.println("Number of Reqeusts: "+size);
        System.out.println("Total size "+countbyte+" Bytes");
        float averageWriting=(float)((duration/size)/Math.pow(10,6));
        System.out.println("Average Writing time per request "+String.format("%.2f",averageWriting)+" ms/request");
        float writeThroughput=(float)(((countbyte*(Math.pow(10,6)))/result));
        System.out.println("Write throughtput is "+String.format("%.2f", writeThroughput)+"KB per Second");
        assertTrue(true);

        //Measure the get time
        final long newstartTime = System.nanoTime();
        for (String key : Database.keySet()) {

            try {
                KVMessage temp = kvClient.get(key);
                assertTrue(temp.getValue().equals(Database.get(key)));
            } catch (Exception e) {
                ex=e;
            }
        }

        final long newduration = System.nanoTime() - newstartTime;
        long newresult = newduration;
        float averageread = (float) ((newduration / size) / Math.pow(10, 6));
        System.out.println("Average Read Time per request " + String.format("%.2f", averageread) + " ms/request");
        //System.out.println("Average Read ThroughPut"+String.valueOf(1/(newresult/newsize)));
        float readThroughput = (float) (((countbyte * (Math.pow(10, 6))) / newresult));

        System.out.println("Read throughtput is " + String.format("%.2f", readThroughput) + "KB per Second");
        kvClient.disconnect();
    }




    @Test
    public void testReplicaPerformanceMultiple() {
        setUpClientConnection();
        KVMessage response = null;
        Exception ex = null;
        testconnect();
        HashMap<String,String>Database=getTestSamples();
        int size=Database.size();
        final long startTime = System.nanoTime();
        int countbyte=0;
        for (String key : Database.keySet()) {
            try {
                KVMessage temp = kvClient.put(key, Database.get(key));
                countbyte+=Database.get(key).length();
            } catch (Exception e) {
                ex=e;
            }
        }
        int NumberofThread=5;
        ClientWrapper MutlipleClients[] = SetUpMultipleClientConnection(NumberofThread,Database);
        Thread threads[] = new Thread[NumberofThread];
        int i = 0;
        for (i = 0; i < NumberofThread; i++) {
            threads[i] = new Thread(MutlipleClients[i]);
            threads[i].start();
        }
        for (i = 0; i < threads.length; i++) {
            try {
                threads[i].join();
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
        for (i = 0; i < MutlipleClients.length; i++) {
            if (MutlipleClients[i].Fail) {
                assertTrue(false);
            }
        }


    }

    @Test
    public void testReplicaPut() {
        setUpClientConnection();
        String key = "key9";
        String value = "bar";
        KVMessage response = null;
        Exception ex = null;
        try {
            kvClient.connect();
        } catch (Exception e) {
            ex = e;
        }

        try {
            while (true) {
                if (response != null && response.getStatus() != KVMessage.StatusType.SERVER_STOPPED)
                    break;
                response = kvClient.put(key, value);
                ecsApp.start();
            }
            // connect to its replica
            kvClient.disconnect();
            kvClient = new KVStore("localhost", 50000);
            kvClient.connect();
            response = kvClient.get(key);
            kvClient.disconnect();
        } catch (Exception e) {
            ex = e;
        }
        assertTrue(ex == null && response.getValue().equals("bar"));
    }

    @Test
    public void testReplicaUpdate() {
        setUpClientConnection();
        String key = "key8";
        String value = "bar";
        KVMessage response = null;
        Exception ex = null;
        try {
            kvClient.connect();
        } catch (Exception e) {
            ex = e;
        }

        try {
            while (true) {
                if (response != null && response.getStatus() != KVMessage.StatusType.SERVER_STOPPED)
                    break;
                response = kvClient.put(key, value);
                ecsApp.start();
            }

            // connect to replica and check if the key is there
            KVStore kvClient2 = new KVStore("localhost", 50000);
            kvClient2.connect();
            response = kvClient2.get(key);
            assertTrue(ex == null && response.getValue().equals("bar"));

            // update in the coordinator, it should be updated in the replica
            kvClient.put(key, "bar2");
            response = kvClient2.get(key);

            // disconnect
            kvClient.disconnect();
            kvClient2.disconnect();
        } catch (Exception e) {
            ex = e;
        }
        assertTrue(ex == null && response.getValue().equals("bar2"));
    }

    @Test
    public void testReplicaRemoveNode() {
        setUpClientConnection();
        String key = "abc";
        String value = "123";
        KVMessage response = null;
        Exception ex = null;
        try {
            kvClient.connect();
        } catch (Exception e) {
            ex = e;
        }

        try {
            while (true) {
                if (response != null && response.getStatus() != KVMessage.StatusType.SERVER_STOPPED)
                    break;
                response = kvClient.put(key, value);
                ecsApp.start();
            }
            Collection<String> removed = new ArrayList<>();
            removed.add("server1:127.0.0.1:50000");
            ecsApp.removeNodes(removed);

            // connect to replica and check if the key is there
            KVStore kvClient2 = new KVStore("localhost", 50006);
            kvClient2.connect();
            response = kvClient2.get(key);

            // disconnect
            kvClient.disconnect();
            kvClient2.disconnect();
        } catch (Exception e) {
            ex = e;
        }
        assertTrue(ex == null && response.getValue().equals("123"));
    }


    @Test
    public void testReplicaDelete() {
        setUpClientConnection();
        String key = "TestKey";
        String value = "bar";
        KVMessage response = null;
        Exception ex = null;

        try {
            kvClient.connect();
        } catch (Exception e) {
            ex = e;
        }

        try {
            while (true) {
                if (response != null && response.getStatus() != KVMessage.StatusType.SERVER_STOPPED)
                    break;
                ecsApp.start();
                response = kvClient.put(key, value);
            }
            response = kvClient.get(key);
        } catch (Exception e) {
            ex = e;
        }

        assertTrue(ex == null && response.getValue().equals("bar"));

        try {
            response = kvClient.put(key, null);
        } catch (Exception e) {
            ex = e;
        }

        assertTrue(ex == null && response.getStatus() == KVMessage.StatusType.DELETE_SUCCESS);

        kvClient.disconnect();

        KVStore replicaClient = new KVStore("localhost", 50006);

        try {
            replicaClient.connect();
        } catch (Exception e) {
            ex = e;
        }
        try {
            ecsApp.start();
            response = replicaClient.get(key);
        } catch (Exception e) {
            ex = e;
        }

        assertTrue(ex == null && response.getStatus() == KVMessage.StatusType.GET_ERROR);
    }

    @Test
    public void testCrashRecoveryServerReplica () {
        String key = "TestCrash_Key";
        String value = "cow";
        KVMessage response = null;
        Exception ex = null;

        KVStore Client = new KVStore("localhost", 50001, true);

        try {
            Client.connect();

            while (true) {
                if (response != null && response.getStatus() != KVMessage.StatusType.SERVER_STOPPED)
                    break;
                ecsApp.start();
                response = Client.put(key, value);
            }

            response = Client.get(key);
        } catch (Exception e) {
            ex = e;
        }

        assertTrue(ex == null && response.getValue().equals("cow"));

        int port = Client.getPortNumber();

        String script = String.format(KILL_TEST, port);

        try {
            Runtime.getRuntime().exec(script);
            Thread.sleep(5000);
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
            ex = e;
        }

        response = null;

        try {
            while (true) {
                if (response != null && response.getStatus() != KVMessage.StatusType.SERVER_STOPPED)
                    break;
                ecsApp.start();
                response = Client.get(key);
            }
        } catch (Exception e) {
            ex = e;
        }

        assertTrue(ex == null && response.getValue().equals("cow"));
    }

    @Test
    public void testCrashRecoveryServerCount () {
        Exception ex = null;

        int count = ecsApp.getServerCount();

        String script = String.format(KILL_TEST, 50001);

        try {
            Runtime.getRuntime().exec(script);
            Thread.sleep(3000);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            ex = e;
        }

        int new_count = ecsApp.getServerCount();

        assertTrue(ex == null && count == new_count);
    }

    @Test
    public void testReplicaAddNode () {
        String key = "TestAdd_Key";
        String value = "baboon";
        KVMessage response = null;
        Exception ex = null;
        ArrayList<String> nodeNames = new ArrayList<>();
        nodeNames.add("server2:127.0.0.1:50001");
        ecsApp.removeNodes(nodeNames);

        IECSNode node = ecsApp.addNode("FIFO", 10);

        KVStore Client = new KVStore(node.getNodeHost(), node.getNodePort(), true);

        try {
            ecsApp.start();
            Client.connect();
            while (true) {
                if (response != null && response.getStatus() != KVMessage.StatusType.SERVER_STOPPED)
                    break;
                ecsApp.start();
                response = Client.put(key, value);
            }
            response = Client.get(key);
        } catch (Exception e){
            ex = e;
        }
        assertTrue(ex == null && response.getValue().equals("baboon"));

        String replicaAdd = Client.getReplicaOneAdd();
        int replicaPort = Client.getReplicaOnePort();

        Client.disconnect();

        Client = new KVStore(replicaAdd, replicaPort);

        try {
            Client.connect();
            while (true) {
                if (response != null && response.getStatus() != KVMessage.StatusType.SERVER_STOPPED)
                    break;
                ecsApp.start();
                response = Client.get(key);
            }
        } catch (Exception e) {
            ex = e;
        }

        assertTrue(ex == null && response.getStatus() == KVMessage.StatusType.GET_SUCCESS);
    }
}