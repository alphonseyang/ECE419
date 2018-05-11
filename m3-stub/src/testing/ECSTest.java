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

public class ECSTest extends TestCase {
    private ECSClient ecsApp;
    private KVStore kvClient;
    private CountDownLatch connectedSignal = new CountDownLatch(1);
    private  int numGetClients = 5;

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
        	Thread.sleep(3000);
            kvClient = null;
        } catch (Exception e) {
            System.out.println("ECS Test error "+e);
        }
    }

    public void tearDown() {
        ecsApp.shutdown();
    }

    public void setUpClientConnection(){
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
    public ClientWrapper[] SetUpMultipleClientConnection(int NumberOfClients)
    {
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
            MutlipleClients[i] = new ClientWrapper(host, PortNumber, "" + i, 50, ClientWrapper.TestType.MoveDataTest);

        }
        return MutlipleClients;

    }
    public ArrayList<IECSNode> GetAvailableNodes()
    {
        ArrayList<IECSNode> nodes=new ArrayList<>();

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

    @Test
    public void testConnection() {
        setUpClientConnection();
        Exception ex = null;
        try {
            kvClient.connect();
        } catch (Exception e) {
            ex = e;
        }
        // ecsApp.stop();
        assertNull(ex);
        return;
    }


    @Test
    public void testPut() {
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
            }
            response = kvClient.get(key);
        } catch (Exception e) {
            ex = e;
        }

        //ecsApp.stop();
        assertTrue(ex == null && response.getValue().equals("bar"));

    }

/*

    @Test
    public void testMoveDataMultipletoOne() {
       // ecsApp.start();

        ClientWrapper MutlipleClients[] = SetUpMultipleClientConnection(numGetClients);
        Thread threads[] = new Thread[numGetClients];
        int i = 0;
        for (i = 0; i < numGetClients; i++) {
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

        ArrayList<String>testKey= new ArrayList();
        ArrayList<IECSNode> AvailableNodes=GetAvailableNodes();
        Collection<String> nodeNames=new ArrayList<>();
        for(IECSNode node:AvailableNodes)
        {
            nodeNames.add(node.getNodeName());
        }
        String AvailableServer= (String) (nodeNames.toArray())[nodeNames.size()-1];
        nodeNames.remove(AvailableServer);
        ecsApp.removeNodes(nodeNames);

        String[] tempresult=AvailableServer.split(":");
        KVStore kvClient = new KVStore(tempresult[1], Integer.parseInt(tempresult[2]));
        try {
            kvClient.connect();
        } catch (Exception e) {

        }

        for( i=0;i<numGetClients;i++)
        {
            Exception ex=null;
            KVMessage result=null;
            try{
                result=kvClient.get("key"+i);
            }
            catch (Exception e)
            {
                ex=e;
                System.out.println(ex.getMessage());

            }
            assertTrue(ex == null && (result.getStatus() == KVMessage.StatusType.GET_SUCCESS) && (result.getValue().equals("value"+i)));


        }

        assertTrue(true);
    }
*/
    @Test
    public void testReconnection(){
        kvClient = new KVStore("localhost", 50000);
        KVMessage response = null;
        try {
            kvClient.connect();
        } catch (Exception e) {
            System.out.println("ECS Test testStop connection failure "+e);
        }
        try {
            response = kvClient.put("a", "1");
        } catch (Exception e){
            System.out.println("ECS Test testStop put error "+e);
        }
        assertTrue(kvClient.getPortNumber() != 50000);
    }

    /*
    @Test
    public void testMoveDataSingleToMultiple() {

        ecsApp.shutdown();

        try {
            ecsApp = new ECSClient("ecs.config");
            ecsApp.addSSHName("localhost");
            ecsApp.initiateStorageService(1, 10, "FIFO");
            ecsApp.start();
            kvClient = null;
        } catch (Exception e) {
            System.out.println("ECS Test error "+e);
        }
        setUpClientConnection();

        KVMessage response = null;
        Exception ex = null;
        try {
            kvClient.connect();
        } catch (Exception e) {
            ex = e;
        }

        try {
            for(int i=0;i<numGetClients;i++)
            {
                String key = "key"+i;
                String value = "value"+i;
                kvClient.put(key, value);
                response = kvClient.get(key);
                assertTrue(ex == null && response.getValue().equals("value"+i));
            }

        } catch (Exception e) {
            ex = e;
        }
        //ecsApp.addNodes(3,"FIFO",10);
        ecsApp.addNode("FIFO",10);
        ecsApp.addNode("FIFO",10);
        ecsApp.addNode("FIFO",10);
        ecsApp.start();


        KVStore kvClient1;
        kvClient1=new KVStore("127.0.0.1",50001);
        try {
            kvClient1.connect();
        }
        catch (Exception e) {
            ex = e;
        }



            for(int i=0;i<numGetClients;i++)
            {
                ex = null;
                String key = "key"+i;
                String value = "value"+i;
                //kvClient.put(key, value);
                try {
                response = kvClient1.get(key);
                } catch (Exception e) {
                    ex = e;
                }
                assertTrue(ex == null &&response.getStatus()== KVMessage.StatusType.GET_SUCCESS&& response.getValue().equals("value"+i));
            }


    }
    */

    @Test
    public void testSingleDelete()
    {
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
            kvClient.put(key, value);
            response = kvClient.get(key);
        } catch (Exception e) {
            ex = e;
        }

        //ecsApp.stop();
        assertTrue(ex == null && response.getValue().equals("bar"));

        try {
            kvClient.put(key, "null");
            response = kvClient.get(key);
        } catch (Exception e) {
            ex = e;
        }
        assertTrue(ex == null && response.getStatus()== KVMessage.StatusType.GET_ERROR);

    }

/*
    @Test
    public void testZookeeper() {
        //tearDown();

        //setUp();

        Exception ex = null;

        ZooKeeper zk = ecsApp.getZK();
        int count = 0;
        try {
                List<String> list = zk.getChildren("/AwaitNode", false);
                count = list.size();
        } catch (KeeperException ke) {
            ex = ke;
        } catch (InterruptedException ie) {
            ex = ie;
        }

        assertTrue(ex == null && count == 3);

        try {
            Thread.sleep(4000);
        } catch (InterruptedException ie) {
            ex = ie;
        }

        for (int i = 0; i < 1; i++) {
            ecsApp.addNode("FIFO", 10);
            //ecsApp.start();
        }

        try {
            List<String> list = zk.getChildren("/AwaitNode", false);
            count = list.size();
        } catch (KeeperException ke) {
            ex = ke;
        } catch (InterruptedException ie) {
            ex = ie;
        }
        assertTrue(ex == null && count == 4);

        tearDown();
    }
*/

    @Test
    public void testStop() {
        setUpClientConnection();
        ecsApp.stop();
        KVMessage response = null;
        try {
            kvClient.connect();
        } catch (Exception e) {
            System.out.println("ECS Test testStop connection failure "+e);
        }

        try {
            response = kvClient.put("testKey", "testValue");
        } catch (Exception e){
            System.out.println("ECS Test testStop put error "+e);
        }
        assertTrue(response != null && response.getStatus() == KVMessage.StatusType.SERVER_STOPPED);

        try {
            response = kvClient.get("testKey");
        } catch (Exception e){
            System.out.println("ECS Test test stop put error "+e);
        }

        assertTrue(response != null && response.getStatus() == KVMessage.StatusType.SERVER_STOPPED);
    }

    @Test
    public void testDelete(){
        setUpClientConnection();
        try {
            kvClient.connect();
        } catch (Exception e) {
            System.out.println("ECS Test testStop connection failure "+e);
        }
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ie){
            System.out.println(ie);
        }
        for(int i=0;i<numGetClients;i++)
        {
            String key="testkey"+i;
            String Value="Value"+i;
            try
            {
                kvClient.put(key,Value);
                if(i%2==0)
                    kvClient.put(key,"null");

            }catch (Exception e) {
            System.out.println("Test Delete Error"+e);
        }

        }
        ArrayList<IECSNode> AvailableNodes=GetAvailableNodes();
        Collection<String> nodeNames=new ArrayList<>();
        for(IECSNode node:AvailableNodes)
        {
            nodeNames.add(node.getNodeName());
        }
        String AvailableServer= (String) (nodeNames.toArray())[nodeNames.size()-1];
        nodeNames.remove(AvailableServer);
        ecsApp.removeNodes(nodeNames);
        String[] tempresult=AvailableServer.split(":");
        KVStore kvClient2 = new KVStore(tempresult[1], Integer.parseInt(tempresult[2]));
        try {
            kvClient2.connect();
        } catch (Exception e) {
            System.out.println("Test Delete Error"+e);

        }
        for(int i=0;i<numGetClients;i++) {
            String key = "testkey" + i;
            KVMessage response = null;
            Exception ex = null;
            if (i % 2 == 0) {
                try {
                    response = kvClient2.get(key);
                } catch (Exception e) {
                    ex = e;
                    System.out.println(e);
                }
                assertTrue(ex == null && response.getStatus() == KVMessage.StatusType.GET_ERROR);

            } else {

                try {
                    response = kvClient2.get(key);
                } catch (Exception e) {
                    ex = e;
                    System.out.println(e);
                }
                assertTrue(ex == null && response.getStatus() == KVMessage.StatusType.GET_SUCCESS);

            }
        }

    }

	
	@Test
		public void testConsistentHashing(){
			setUpClientConnection();
		    String key = "a";
		    String value = "bar";
		    KVMessage response = null;
		    Exception ex = null;
		    try {
		        kvClient.connect();
		    } catch (Exception e) {
		        ex = e;
		    }

		    try {
		        response = kvClient.put(key, value);
		    } catch (Exception e){
		        System.out.println("ECS Test testStop put error "+e);
		    }
			ArrayList<String> list = new ArrayList<>();
			list.add("server2:127.0.0.1:50001");
			ecsApp.removeNodes(list);
			kvClient = new KVStore("localhost", 50000);
			try {
		        kvClient.connect();
		    } catch (Exception e) {
		        ex = e;
		    }

			try {
		        kvClient.put(key, value);
		        response = kvClient.get(key);
		    } catch (Exception e) {
		        ex = e;
		    }
		    assertTrue(response != null && response.getValue().equals(value));
		}

}
