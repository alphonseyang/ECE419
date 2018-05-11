package app_kvServer;

import java.io.*;
import java.math.BigInteger;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.TreeMap;
import java.net.UnknownHostException;

import common.datatypes.MD5;
import ecs.IECSNode;
import logger.LogSetup;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.zookeeper.*;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.data.Stat;

import common.communication.KVCommunication;
import persistent_storage.StorageManager;
import app_kvServer.kvCache.cacheController;
import common.datatypes.MetaData;
import client.KVStore;
import common.KVMessage;

public class KVServer implements IKVServer {

    public enum ServerStateType {
        IDLE,
        STOPPED,
        STARTED,
        SHUT_DOWN,
        ERROR
    }

    private static Logger logger = Logger.getRootLogger();
    private int port;
    private int cacheSize;
    private String strategy;
    private ServerSocket serverSocket;
    private boolean running;
    //Map synchronizedMap;
    private StorageManager DataManager;
    private cacheController Cache;
    private ArrayList<Thread> threadList;
    private Thread serverThread;
    private boolean isOldVersion = false;

    // milestone 2
    private String serverName;
    private String zkHostName;
    private int zkPort;
    private TreeMap<BigInteger, MetaData> metaData;
    private MetaData newMetaDataObject;
    private ServerStateType serverState;
    private boolean writeLocked;
    private ZooKeeper zk;
    final CountDownLatch connectedSignal = new CountDownLatch(1);
    private boolean initialized = false;
    private boolean isIPSet = false;
    private boolean replicaStarted = false;
    private MetaData replicaOne = null;
    private MetaData replicaTwo = null;
    private MetaData coordinatorOne = null;
    private MetaData coordinatorTwo = null;
    private BigInteger serverNameHash = null;
    private ArrayList<KVStore> replicaCommunicators = new ArrayList<>();
    private ArrayList<KVCommunication> communicators = new ArrayList<>();
    private boolean updateReplica = false;


    //Key->Key : Value:->Client Identifier
    private TreeMap<String,ArrayList<String>>Notification_Mapping= new TreeMap<>();

    //Key->Client: Value->Messages
    private TreeMap<String,ArrayList<String>>MessagePool=new TreeMap<>();


    /**
     * Start KV Server with selected name
     *
     * @param name       unique name of server
     * @param zkHostname hostname where zookeeper is running
     * @param zkPort     port where zookeeper is running
     */

    public KVServer(String name, String zkHostname, int zkPort) {
        this.zkPort = zkPort;
        this.zkHostName = zkHostname;
        this.serverName = name;
        try {
            serverNameHash = MD5.HashInBI(this.serverName);
        } catch (NoSuchAlgorithmException nsae) {
            logger.error("Server name hash " + nsae);
        }
        threadList = new ArrayList<Thread>();
        serverThread = null;
        String[] serverInfo = this.serverName.split(":");
        this.port = Integer.parseInt(serverInfo[2]);
        this.DataManager = new StorageManager(port);
        this.DataManager.initilizateStoreManager();
        this.serverState = ServerStateType.STOPPED;
        this.writeLocked = false;
        connectZooKeeper(zkHostName, zkPort);
        getMetaDataFromZK();
        createAwaitNodes();
        initKVServer(metaData, cacheSize, strategy);
        initialized = true;

        // start the server in stopped state
        this.run();
    }

    /**
     * Start KV Server at given port
     *
     * @param port      given port for storage server to operate
     * @param cacheSize specifies how many key-value pairs the server is allowed
     *                  to keep in-memory
     * @param strategy  specifies the cache replacement strategy in case the cache
     *                  is full and there is a GET- or PUT-request on a key that is
     *                  currently not contained in the cache. Options are "FIFO", "LRU",
     *                  and "LFU".
     */
    public KVServer(int port, int cacheSize, String strategy) {
        this.isOldVersion = true;
        this.port = port;
        this.cacheSize = cacheSize;
        this.strategy = strategy;
        threadList = new ArrayList<Thread>();
        serverThread = null;
        this.DataManager = new StorageManager(port);
        this.DataManager.initilizateStoreManager();
        this.Cache = new cacheController(cacheSize, strategy);
    }

    @Override
    public int getPort() {
        return this.serverSocket.getLocalPort();
    }

    @Override
    public String getHostname() {
        if (serverSocket != null)
            return serverSocket.getInetAddress().getHostName();
        else
            return null;
    }

    @Override
    public CacheStrategy getCacheStrategy() {
        switch (this.strategy) {
            case "FIFO":
                return IKVServer.CacheStrategy.FIFO;
            case "LRU":
                return IKVServer.CacheStrategy.LRU;
            case "LFU":
                return IKVServer.CacheStrategy.LFU;
            default:
                return IKVServer.CacheStrategy.None;
        }
    }

    @Override
    public int getCacheSize() {
        return this.cacheSize;
    }

    @Override
    public boolean inStorage(String key) {
        String result = null;
        try {
            result = DataManager.GetKV(key);

        } catch (Exception e) {
            logger.error(e);
        }

        return result != null;
    }

    @Override
    public boolean inCache(String key) {
        if (Cache.isHascache()) {
            return Cache.inCache(key);
        }
        return false;
    }

    @Override
    public String getKV(String key) throws Exception {
        //StorageManager test= new StorageManager(key,null,null);
        String result = null;

        try {
            if (Cache.isHascache()) {
                result = Cache.get(key);
                if (result != null) {
                    return result;
                }
            }
            if (result == null) {
                result = DataManager.GetKV(key);
                if (Cache.isHascache() && result != null) {
                    Cache.set(key, result);
                }
            }
        } catch (Exception e) {
            logger.error(e);
            throw e;
        }


        return result;
    }

    @Override
    public void putKV(String key, String value) {
        //If key exist in the look up table, we should do an update command
        DataManager.PutKV(key, value);
        this.notify_Clients(key,value);
        // Put KV into cache.
        if (Cache.isHascache()) {
            Cache.set(key, value);
        }
    }

    public void deleteKV(String key) {
        DataManager.DeleteKV(key);

        this.notify_Clients(key,null);

        if (Cache.isHascache()) {
            Cache.delete(key);
        }
    }

    @Override
    public void clearCache() {
        if (Cache.isHascache()) {
            Cache.clearCache();
        }
    }

    @Override
    public void clearStorage() {
        clearCache();
        DataManager.clearStorage();
    }

    @Override
    public void kill() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            logger.error("Error! " +
                    "Unable to close socket on port: " + port, e);
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            for (KVStore client : replicaCommunicators) {
                logger.info("close port number in communicator " + client.getPortNumber() + ": and in server" + this.getPort());
                client.notifyServerSideCloseChannel();
                client.disconnect();
            }

            for (int i = 0; i < threadList.size(); i++) {
                //communicators.get(i).closeCommunication();
                threadList.get(i).interrupt();
            }

            if (serverThread != null)
                serverThread.interrupt();
            serverSocket.close();
        } catch (IOException e) {
            logger.error("Error! " +
                    "Unable to close socket on port: " + port + e);
			try{
				serverSocket.close();
			} catch (IOException ioe){
				logger.error("Server socket close error ");
			}
        } catch (Exception e){
            logger.error("Send closing message error " + e);
        }
    }

    @Override
    public void start() {
        this.serverState = ServerStateType.STARTED;
    }

    @Override
    public void stop() {
        this.serverState = ServerStateType.STOPPED;
    }

    @Override
    public void lockWrite() {
        this.writeLocked = true;
    }

    @Override
    public void unlockWrite() {
        this.writeLocked = false;
    }

    @Override
    public boolean moveData(String[] hashRange, String targetName) throws Exception {
        BigInteger hashTarget = MD5.HashInBI(targetName);
        MetaData data = this.metaData.get(hashTarget);
        if (data == null)
            data = newMetaDataObject;
        String address = data.getHost();
        int port = data.getPort();

        //Need addresss & port
        if (hashRange.length < 2)
            return false;
        //return byte array of Data
        String DataResult = DataManager.GetMovedData(hashRange);
        //Build a connection to Server as a Temp Client


        KVStore tempClient = new KVStore(address, port);
        tempClient.connect();
        KVMessage result = tempClient.moveData(DataResult);
        tempClient.notifyServerSideCloseChannel();
        tempClient.disconnect();

        if (result.getCommunicationType() == KVMessage.CommunicationType.ECS_ReceiveDataFinished) {
            DataManager.deleteKVPairinRange(hashRange);
            return true;

        }
        //
        System.out.println("IT SHOULD NOT REACH HERE!!!!!!");
        return false;
    }

    @Override
    public void update(IECSNode node) {
        TreeMap<BigInteger, MetaData> newMetaData = node.getMetaData();
        updateReplica = false;
        switch (node.getFlag()) {
            case STOP:
                if (this.serverState != ServerStateType.STOPPED) {
                    stop();
                }
                break;
            case START:
                if (this.serverState != ServerStateType.STARTED) {
                    while (!initialized) {
                        // wait till initialization finished
                    }
                    start();
                }
                break;
            case SHUT_DOWN:
                if (this.serverState != ServerStateType.SHUT_DOWN) {
                    close();
                }
                break;
            case KV_TRANSFER:
                lockWrite();
                if (newMetaData.get(serverNameHash) == null) {
                    // this server node is removed
                    for (Map.Entry<BigInteger, MetaData> entry : newMetaData.entrySet()) {
                        if (entry.getValue().getHashRange()[0].equals(metaData.get(serverNameHash).getHashRange()[0])) {
                            try {
                                if (moveData(metaData.get(serverNameHash).getHashRange(), entry.getValue().getName())) {
                                    ackTransfer(node);
                                    unlockWrite();
                                    close();
                                } else
                                    logger.error("KV transfer failed");
                                break;
                            } catch (Exception e) {
                                logger.error("Error in removing node migrating data " + e);
                            }
                        }
                    }
                } else {
                    // a new node is added, find the next server in the metadata map
                    MetaData temp = null;
                    // if the current one is with the highest hash value
                    if (newMetaData.lowerEntry(serverNameHash) == null)
                        temp = newMetaData.lastEntry().getValue();
                    else
                        temp = newMetaData.lowerEntry(serverNameHash).getValue();
                    newMetaDataObject = temp;
                    try {
                        if (moveData(new String[]{metaData.get(serverNameHash).getHashRange()[0], temp.getHashRange()[1]}, temp.getName())) {
                            ackTransfer(node);
                            unlockWrite();
                        } else
                            logger.error("KV transfer failed");
                    } catch (Exception e) {
                        logger.error("Error in removing node migrating data " + e);
                    }
                }
                break;
            case START_REPLICA:
                startReplicas();
                break;
            case UPDATE:
                updateReplica = true;
                break;
            default:
                break;
        }
        this.cacheSize = node.getCacheSize();
        this.strategy = node.getCacheStrategy();
        this.metaData = newMetaData;
		setIPAddress(node);
        updateCoordinatorInfo();
        updateReplicaInfo();
        
    }

    public boolean ReceivedData(String data) {

        DataManager.ParseKVpairs(data.getBytes());
        return true;

    }

    public void run() {
        new KVServerWrapper(this);
    }

    public void loopForConnection() {
        running = initializeKVServer();

        if (serverSocket != null) {
            while (isRunning()) {
                try {
                    Socket client = serverSocket.accept();
                    KVCommunication communicator =
                            new KVCommunication(client, this);
                    Thread t = new Thread(communicator);
                    t.start();
                    threadList.add(t);
                    communicators.add(communicator);

                    logger.info("Connected to "
                            + client.getInetAddress().getHostName()
                            + " on port " + client.getPort());
                } catch (IOException e) {
                    logger.info("Server socket closed or error " + port + " " + e);
                }
            }
        }
    }

    public ServerStateType getServerState() {
        return serverState;
    }

    public boolean isWriterLocked() {
        return writeLocked;
    }

    public boolean isOldVersion() {
        return isOldVersion;
    }

    public boolean isPutResponsibleServer(String key) {
        try {
            BigInteger keyHash = MD5.HashInBI(key);
            MetaData data = metaData.get(serverNameHash);
            String[] range = data.getHashRange();

            // compare the keyHash with ranges
            BigInteger upper = new BigInteger(range[0]);
            BigInteger lower = new BigInteger(range[1]);
            return inHashRange(keyHash, upper, lower);
        } catch (NoSuchAlgorithmException nsae) {
            logger.error("Server responsible error " + nsae);
        }

        return false;
    }

    public boolean isGetResponsibleServer(String key) {
        try {
            BigInteger keyHash = MD5.HashInBI(key);
            MetaData data = metaData.get(serverNameHash);
            String[] range = data.getHashRange();

            // compare the keyHash with ranges
            BigInteger upper = new BigInteger(range[0]);
            if (coordinatorTwo != null) {
                upper = new BigInteger(coordinatorTwo.getHashRange()[0]);
            } else if (coordinatorOne != null) {
                upper = new BigInteger(coordinatorOne.getHashRange()[0]);
            }
            BigInteger lower = new BigInteger(range[1]);
            return inHashRange(keyHash, upper, lower);
        } catch (NoSuchAlgorithmException nsae) {
            logger.error("Server responsible error " + nsae);
        }

        return false;
    }

    public TreeMap<BigInteger, MetaData> getMetaData() {
        return metaData;
    }

    public void updateReplicaWithKV(String key, String value) throws Exception {
        for (int i = 0; i < replicaCommunicators.size(); i++) {
            KVStore client = replicaCommunicators.get(i);
            // only allows the two replicas to update
            if ((replicaOne != null && replicaOne.getPort() == client.getPortNumber()) ||
                    replicaTwo != null && replicaTwo.getPort() == client.getPortNumber()) {
                logger.info(getPort() + "send key " + key + " to replica");
                KVMessage response = client.sendCommunicationMessage(key, value, KVMessage.CommunicationType.ECS_ReplicaUpdate);
                if (response == null || response.getCommunicationType() != KVMessage.CommunicationType.ECS_ReplicaUpdateFinished) {
                    logger.error("Failed to update in replica");
                }
            }
        }
    }

    public void storageServiceRecovery(String coordinatorHost, int coordinatorPort) {
        // replica is only responsible for restarting his direct predecessor coordinator
        try {
            // close communication channel
            // remove the replica communicator from list
            for (int i = 0; i < replicaCommunicators.size(); i++) {
                KVStore client = replicaCommunicators.get(i);
                if (client.getPortNumber() == coordinatorPort) {
                    client.disconnect();
                    replicaCommunicators.remove(i);
                }
            }

            if (coordinatorOne.getPort() == coordinatorPort) {
                // update the crash detector to tell the ECS(ZK)
                String path = "/CrashDetector";
                if (zk.exists(path, true) != null) {
                    zk.setData(path, generateByteArray(coordinatorOne), zk.exists(path, true).getVersion());
                }
            }
        } catch (KeeperException ke) {
            logger.error("Notify ECS server crashed error " + ke);
        } catch (InterruptedException ie) {
            logger.error("Notify ECS server crashed error " + ie);
        }
    }

    private boolean isRunning() {
        return this.running;
    }

    private boolean initializeKVServer() {

        logger.info("Initialize server ...");

        try {
            serverSocket = new ServerSocket(port);
            logger.info("Server listening on port: "
                    + serverSocket.getLocalPort());
            return true;

        } catch (IOException e) {
            logger.error("Error! Cannot open server socket:");
            if (e instanceof BindException) {
                logger.error("Port " + port + " is already bound!");
            }
            return false;
        }
    }

    private void connectZooKeeper(String zkHostName, int zkPort) {
        try {
            zk = new ZooKeeper(zkHostName + ":" + zkPort, 300000000, new Watcher() {
                @Override
                public void process(WatchedEvent we) {
                    if (we.getState() == KeeperState.SyncConnected) {
                        connectedSignal.countDown();
                    }
                }
            });
            connectedSignal.await();
        } catch (IOException ioe) {
            logger.error(ioe);
        } catch (InterruptedException ie) {
            logger.error(ie);
        }
    }

    private void getMetaDataFromZK() {
        try {
            if (zk.exists("/" + this.serverName, true) != null) {
                byte[] data = zk.getData("/" + this.serverName, new Watcher() {
                    @Override
                    public void process(WatchedEvent we) {
                        if (we.getType() == Event.EventType.None) {
                            switch (we.getState()) {
                                case Expired:
                                    //connectedSignal.countDown();
                                    break;
                            }
                        } else {
                            try {
                                getMetaDataFromZK();
                            } catch (Exception ex) {
                                System.out.println(ex.getMessage());
                            }
                        }
                    }
                }, null);
                retrieveInformation(data);
                //connectedSignal.await();
            }
        } catch (KeeperException ke) {
            logger.error("KVServer get data" + ke);
        } catch (InterruptedException ie) {
            logger.error(ie);
        }
    }

    private void initKVServer(TreeMap<BigInteger, MetaData> metaData, int cacheSize, String replacementStrategy) {
        // initialize with the information
        this.metaData = metaData;
        this.cacheSize = cacheSize;
        this.strategy = replacementStrategy;
        this.Cache = new cacheController(cacheSize, replacementStrategy);
    }

    private void retrieveInformation(byte[] data) {
        Object obj = null;
        ByteArrayInputStream bis = null;
        ObjectInputStream ois = null;
        try {
            bis = new ByteArrayInputStream(data);
            ois = new ObjectInputStream(bis);
            obj = ois.readObject();
        } catch (IOException ioe) {
            logger.error(ioe);
        } catch (ClassNotFoundException cnfe) {
            logger.error(cnfe);
        } finally {
            try {
                if (bis != null) {
                    bis.close();
                }
                if (ois != null) {
                    ois.close();
                }
            } catch (IOException ioe) {
                logger.error(ioe);
            }
        }

        IECSNode node = (IECSNode) obj;
        update(node);
    }

    private void ackTransfer(IECSNode node) {
        try {
            node.setFlag(IECSNode.ECSNodeFlag.TRANSFER_FINISH);
            byte[] data = generateByteArray(node);
            String path = "/" + node.getNodeName();
            if (zk.exists(path, true) != null)
                zk.setData(path, data, zk.exists(path, true).getVersion());
        } catch (KeeperException | InterruptedException e) {
            logger.error("Ack transfer " + e);
        } catch (Exception e) {
            logger.error("Add node await node exception " + e);
        }
    }

    private byte[] generateByteArray(Object node) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = null;
        byte[] bytes = null;
        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(node);
            out.flush();
            bytes = bos.toByteArray();
        } catch (IOException e) {
            logger.error(e);
        } finally {
            try {
                bos.close();
            } catch (IOException ex) {
                logger.error(ex);
            }
        }

        return bytes;
    }

    private boolean inHashRange(BigInteger keyHash, BigInteger start, BigInteger end) {
        boolean descend = start.compareTo(end) == 1;
        return keyHash.compareTo(start) == 0 ||
                keyHash.compareTo(end) == 0 ||
                (keyHash.compareTo(start) == 1 && keyHash.compareTo(end) == -1 && !descend) ||
                (keyHash.compareTo(start) == -1 && keyHash.compareTo(end) == -1 && descend) ||
                (keyHash.compareTo(start) == 1 && keyHash.compareTo(end) == 1 && descend);
    }

    private IECSNode parseByteArrayToNode(byte[] data) {
        Object obj = null;
        ByteArrayInputStream bis = null;
        ObjectInputStream ois = null;
        try {
            bis = new ByteArrayInputStream(data);
            ois = new ObjectInputStream(bis);
            obj = ois.readObject();
        } catch (IOException ioe) {
            logger.error(ioe);
        } catch (ClassNotFoundException cnfe) {
            logger.error(cnfe);
        } finally {
            try {
                if (bis != null) {
                    bis.close();
                }
                if (ois != null) {
                    ois.close();
                }
            } catch (IOException ioe) {
                logger.error(ioe);
            }
        }

        IECSNode node = (IECSNode) obj;
        return node;
    }

    private void setIPAddress(IECSNode node) {
        //if (!isIPSet) {
        if (true) {
            try {
                String currentIP = InetAddress.getLocalHost().getHostAddress();
                if (!metaData.firstEntry().getValue().getHost().equals(currentIP)) {
                    for (Map.Entry<BigInteger, MetaData> entry : metaData.entrySet()) {
                        MetaData temp = entry.getValue();
                        temp.setHost(currentIP);
                        metaData.put(entry.getKey(), temp);
                    }
/*
                    for (Map.Entry<BigInteger, MetaData> entry : metaData.entrySet()) {
                        String path = "/" + entry.getValue().getName();
                        if (zk.exists(path, true) != null) {
                            byte[] data = zk.getData(path, true, null);
                            node = parseByteArrayToNode(data);
							node.setFlag(IECSNode.ECSNodeFlag.STATE_CHANGE);
                            node.setMetaData(metaData);
                            data = generateByteArray(node);
                            if (zk.exists(path, true) != null) {
                                zk.setData(path, data, zk.exists(path, true).getVersion());
                            }
                        }
                    }
*/
                }
            } catch (UnknownHostException uhe) {
                logger.error("Get host address ip failed" + uhe);
            } /*catch (KeeperException ke) {
                logger.error("Update getdata/setdata" + ke);
            } catch (InterruptedException ie) {

            }*/
            isIPSet = true;
        }
    }

    private void createAwaitNodes() {
        // Add current server to AwaitNode in zookeeper as a child
        try {
            if (zk != null) {
                String path = "/AwaitNode";
                Stat s = zk.exists(path, true);
                Stat sNode = zk.exists(path + "/" + this.serverName, true);

                if (s != null && sNode == null) {
                    byte[] b = this.serverName.getBytes();
                    zk.create(path + "/" + this.serverName,
                            b, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                }
            }
        } catch (KeeperException e) {
            logger.error("KVServer create await node " + e);
        } catch (InterruptedException ie) {
            logger.error("KVServer create await node " + ie);
        }
    }

    private void updateReplicaInfo() {
        try {
            if (updateReplica) {
                removeReplicaCommunicator();
            }

            if (metaData.size() == 1) {
                replicaOne = null;
                replicaTwo = null;
            } else if (metaData.size() == 2) {
                BigInteger tempKey = metaData.higherKey(serverNameHash);
                MetaData temp = null;
                if (tempKey != null) {
                    temp = metaData.get(tempKey);
                } else {
                    temp = metaData.firstEntry().getValue();
                }
                if (updateReplica) {
                    // need to update replica
                    startReplica(temp.getPort(), temp.getHost());
                    if (!moveData(metaData.get(serverNameHash).getHashRange(), temp.getName()))
                        logger.error("Failed to move data in reconfig replica");
                }

                replicaOne = temp;
                replicaTwo = null;
            } else {
                BigInteger tempKey = metaData.higherKey(serverNameHash);
                MetaData newReplica1 = null;
                MetaData newReplica2 = null;
                if (tempKey != null) {
                    newReplica1 = metaData.get(tempKey);
                    tempKey = metaData.higherKey(tempKey);
                    if (tempKey != null) {
                        newReplica2 = metaData.get(tempKey);
                    } else {
                        newReplica2 = metaData.firstEntry().getValue();
                    }
                } else {
                    newReplica1 = metaData.firstEntry().getValue();
                    tempKey = metaData.higherKey(metaData.firstKey());
                    newReplica2 = metaData.get(tempKey);
                }

                // start replica and move new data to it
                if (updateReplica) {
                    startReplica(newReplica1.getPort(), newReplica1.getHost());
                    if (!moveData(metaData.get(serverNameHash).getHashRange(), newReplica1.getName()))
                        logger.error("Failed to move data in reconfig replica");

                    startReplica(newReplica2.getPort(), newReplica2.getHost());
                    if (!moveData(metaData.get(serverNameHash).getHashRange(), newReplica2.getName()))
                        logger.error("Failed to move data in reconfig replica");
                }

                replicaOne = newReplica1;
                replicaTwo = newReplica2;
            }
        } catch (Exception e) {
            logger.error("Reconfig replica move data error " + e);
        }
    }

    private void updateCoordinatorInfo() {
        if (metaData.size() == 1) {
            coordinatorOne = null;
            coordinatorTwo = null;
        } else if (metaData.size() == 2) {
            BigInteger tempKey = metaData.lowerKey(serverNameHash);
            MetaData newCo1 = null;
            if (tempKey != null) {
                newCo1 = metaData.get(tempKey);
            } else {
                newCo1 = metaData.lastEntry().getValue();
            }

            if (updateReplica) {
                // remove out of range key value pairs
                BigInteger start = new BigInteger(newCo1.getHashRange()[0]);
                evictOldCoordinatorData(start);
            }

            coordinatorOne = newCo1;
            coordinatorTwo = null;
        } else {
            BigInteger tempKey = metaData.lowerKey(serverNameHash);
            MetaData newCo1 = null;
            MetaData newCo2 = null;
            if (tempKey != null) {
                newCo1 = metaData.get(tempKey);
                tempKey = metaData.lowerKey(tempKey);
                if (tempKey != null) {
                    newCo2 = metaData.get(tempKey);
                } else {
                    newCo2 = metaData.lastEntry().getValue();
                }
            } else {
                newCo1 = metaData.lastEntry().getValue();
                tempKey = metaData.lowerKey(metaData.lastKey());
                newCo2 = metaData.get(tempKey);
            }

            if (updateReplica) {
                // remove out of range key value pairs
                BigInteger start = new BigInteger(newCo2.getHashRange()[0]);
                evictOldCoordinatorData(start);
            }

            coordinatorOne = newCo1;
            coordinatorTwo = newCo2;
        }
    }

    private void startReplicas() {
        if (!replicaStarted) {
            try {
                if (replicaOne != null) {
                    startReplica(replicaOne.getPort(), replicaOne.getHost());
                }
                if (replicaTwo != null) {
                    startReplica(replicaTwo.getPort(), replicaTwo.getHost());
                }
            } catch (Exception e) {
                logger.error("Move Data exception in replicas" + e);
            }
            replicaStarted = true;
        }
    }

    private void startReplica(int port, String host) {
        try {
            KVStore tempClient = new KVStore(host, port);
            tempClient.connect();
            KVMessage response = tempClient.notifyReplica(getPort(), getHostname());
            if (response == null || response.getCommunicationType() != KVMessage.CommunicationType.ECS_ConnectReplicaFinished) {
                logger.error("Failed to notify replica (should not be here)");
            } else {
                replicaCommunicators.add(tempClient);
            }
        } catch (Exception e) {
            logger.error("Failed to connect replica " + port + " " + e);
        }
    }

    public String getReplica() {
        String result = null;

        if (replicaOne != null) {
            result = replicaOne.getHost() + ';' + replicaOne.getPort();
        }

        if (replicaTwo != null) {
            result = result + ';' + replicaTwo.getHost() + ';' + replicaTwo.getPort();
        }

        return result;
    }

    private void removeReplicaCommunicator() {
        try {
            for (int i = 0; i < replicaCommunicators.size(); i++) {
                KVStore client = replicaCommunicators.get(i);
                // close the communication channel with this replica
                client.notifyServerSideCloseChannel();
                client.disconnect();
                replicaCommunicators.remove(i);

            }
        } catch (Exception e) {
            logger.error("Remove replica communicator error " + e);
        }
    }

    private void evictOldCoordinatorData(BigInteger start) {
        BigInteger end = new BigInteger(metaData.get(serverNameHash).getHashRange()[1]);
        BigInteger coStart = null;
        if (coordinatorTwo != null) {
            coStart = new BigInteger(coordinatorTwo.getHashRange()[0]);
        } else if (coordinatorOne != null) {
            coStart = new BigInteger(coordinatorOne.getHashRange()[0]);
        }
        if (coStart != null) {
            BigInteger reverseStart = end.add(new BigInteger("1"));
            BigInteger reverseEnd = start.subtract(new BigInteger("1"));
            if (start.compareTo(end) != reverseStart.compareTo(reverseEnd)) {
                String[] hashRange = new String[]{reverseStart.toString(), reverseEnd.toString()};
                try {
                    DataManager.deleteKVPairinRange(hashRange);
                } catch (Exception e) {
                    logger.error("Clear coordinator hash range error " + e);
                }
            }
        }

    }

    public void add_watch(String Key,String Client_Identifier)
    {
        if(Notification_Mapping.get(Key)==null)
        {
            ArrayList<String>Identifers=new ArrayList<>();
            Identifers.add(Client_Identifier);
            Notification_Mapping.put(Key,Identifers);

        }
        else
        {
            Notification_Mapping.get(Key).add(Client_Identifier);
        }
    }
    public void notify_Clients(String Key,String newValue)

    {
        String Message;
        if(newValue==null)
            Message="Key: "+Key+" is deleted";
        else
            Message=" Key: "+Key+", Value: "+newValue;
        ArrayList<String>Client_List=Notification_Mapping.get(Key);
        if(Client_List==null) return;
        for(String Client:Client_List)
        {
            if(MessagePool.get(Client)==null)
            {
                ArrayList<String>Identifers=new ArrayList<>();
                Identifers.add(Message);
                MessagePool.put(Client,Identifers);
            }
            else
            {
                MessagePool.get(Client).add(Message);

            }
         System.out.println("Test:: notify Client"+Client+" --"+Message);
        }

    }
    public String get_notify_message(String Client)
    {

        ArrayList<String>Notify_Messages=MessagePool.get(Client);
        if(Notify_Messages==null)
            return null;
        else
        {
            String result=MessagePool.get(Client).get(Notify_Messages.size()-1);
            MessagePool.get(Client).remove(result);
            if(MessagePool.get(Client).size()==0)
                MessagePool.put(Client,null);
            return result;

        }

    }
    public void test_watch() {
        for (Map.Entry<String, ArrayList<String>> entry : Notification_Mapping.entrySet()) {
            System.out.println(entry.getKey());
            for (int i = 0; i < entry.getValue().size(); i++) {
                System.out.println(entry.getValue().get(i));
            }


        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("Arguments: ");
            new LogSetup("logs/server.log", Level.INFO);
            if (args.length != 3) {
                logger.error("Error! Invalid number of arguments!");
                logger.error("Usage: Server <name> <ZK Host Name> <ZK Port>!");
            } else {
                String nameArg = args[0];
                String zkHostNameArg = args[1];
                int zkPortArg = Integer.parseInt(args[2]);
                new KVServer(nameArg, zkHostNameArg, zkPortArg);
            }
        } catch (IOException e) {
            logger.error("Error! Unable to initialize logger!");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
