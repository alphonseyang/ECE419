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
import org.omg.CORBA.portable.UnknownException;
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

        if (result == null)
            return false;
        else
            return true;
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
    public void putKV(String key, String value) throws Exception {
        //If key exist in the look up table, we should do an update command
        DataManager.PutKV(key, value);
        // Put KV into cache.
        if (Cache.isHascache()) {
            Cache.set(key, value);
        }
    }

    public void deleteKV(String key) throws Exception {
        DataManager.DeleteKV(key);
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
            for (int i = 0; i < threadList.size(); i++) {
                threadList.get(i).interrupt();
            }
            if (serverThread != null)
                serverThread.interrupt();
            serverSocket.close();
        } catch (IOException e) {
            logger.error("Error! " +
                    "Unable to close socket on port: " + port, e);
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
        tempClient.disconnect();

        if (result.getCommunicationType() == KVMessage.CommunicationType.ECS_ReceiveDataFinished) {
            DataManager.deleteKVPairinRange(hashRange);
            return true;

        }
        //
        System.out.println("IT SHOULD NOT REACH HERE!!!!!!");
        return false;
    }

    public boolean ReceivedData(String data) {

        DataManager.ParseKVpairs(data.getBytes());
        return true;

    }

    @Override
    public void update(IECSNode node) {
        TreeMap<BigInteger, MetaData> newMetaData = node.getMetaData();
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
                BigInteger hashValue = null;
                try {
                    hashValue = MD5.HashInBI(this.serverName);
                } catch (NoSuchAlgorithmException nsae) {
                    logger.error("Server hash exception " + nsae);
                }
                if (newMetaData.get(hashValue) == null) {
                    // this server node is removed
                    for (Map.Entry<BigInteger, MetaData> entry : newMetaData.entrySet()) {
                        if (entry.getValue().getHashRange()[0].equals(metaData.get(hashValue).getHashRange()[0])) {
                            try {
                                if (moveData(metaData.get(hashValue).getHashRange(), entry.getValue().getName())) {
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
                    if (newMetaData.lowerEntry(hashValue) == null)
                        temp = newMetaData.lastEntry().getValue();
                    else
                        temp = newMetaData.lowerEntry(hashValue).getValue();
                    newMetaDataObject = temp;
                    try {
                        if (moveData(new String[]{metaData.get(hashValue).getHashRange()[0], temp.getHashRange()[1]}, temp.getName())) {
                            ackTransfer(node);
                            unlockWrite();
                        } else
                            logger.error("KV transfer failed");
                    } catch (Exception e) {
                        logger.error("Error in removing node migrating data " + e);
                    }
                }
                break;
            default:
                break;
        }
        this.cacheSize = node.getCacheSize();
        this.strategy = node.getCacheStrategy();
        this.metaData = newMetaData;
        try {
            String currentIP = InetAddress.getLocalHost().getHostAddress();
            for (Map.Entry<BigInteger, MetaData> entry : metaData.entrySet()){
                MetaData temp = entry.getValue();
                temp.setHost(currentIP);
                metaData.put(entry.getKey(), temp);
            }
            for (Map.Entry<BigInteger, MetaData> entry : metaData.entrySet()){
                String path = "/"+entry.getValue().getName();
                byte[] data = zk.getData(path, true, null);
                node = parseByteArrayToNode(data);
                node.setMetaData(metaData);
                data = generateByteArray(node);
                zk.setData(path, data, zk.exists(path, true).getVersion());
            }
        } catch (UnknownHostException uhe)
        {
            logger.error("Get host address ip failed" + uhe);
        } catch (KeeperException ke){

        } catch (InterruptedException ie){

        }

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

                    logger.info("Connected to "
                            + client.getInetAddress().getHostName()
                            + " on port " + client.getPort());
                } catch (IOException e) {
                    logger.error("Error! " +
                            "Unable to establish connection. \n", e);
                }
            }
        }
        logger.info("Server stopped.");
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

    public boolean isResponsibleServer(String key) {
        try {
            BigInteger keyHash = MD5.HashInBI(key);
            BigInteger serverHash = MD5.HashInBI(this.serverName);
            MetaData data = metaData.get(serverHash);
            String[] range = data.getHashRange();

            // compare the keyHash with ranges
            BigInteger upper = new BigInteger(range[0]);
            BigInteger lower = new BigInteger(range[1]);
            boolean descend = upper.compareTo(lower) == 1;
            if (keyHash.compareTo(upper) == 0 ||
                    keyHash.compareTo(lower) == 0 ||
                    (keyHash.compareTo(upper) == 1 && keyHash.compareTo(lower) == -1 && !descend) ||
                    (keyHash.compareTo(upper) == -1 && keyHash.compareTo(lower) == -1 && descend) ||
                    (keyHash.compareTo(upper) == 1 && keyHash.compareTo(lower) == 1 && descend)) {
                return true;
            } else {
                return false;
            }
        } catch (NoSuchAlgorithmException nsae) {
            logger.error("Server responsible error " + nsae);
        }

        return false;
    }

    public TreeMap<BigInteger, MetaData> getMetaData() {
        return metaData;
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
            byte[] data = zk.getData("/" + this.serverName, new Watcher() {
                @Override
                public void process(WatchedEvent we) {
                    if (we.getType() == Event.EventType.None) {
                        switch (we.getState()) {
                            case Expired:
                                connectedSignal.countDown();
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

            // Add current server to AwaitNode in zookeeper as a child
            if (zk != null) {
                try {
                    String path = "/AwaitNode";
                    Stat s = zk.exists(path, false);
                    Stat sNode = zk.exists(path + "/" + this.serverName, false);

                    if (s != null && sNode == null) {
                        byte[] b = this.serverName.getBytes();
                        zk.create(path + "/" + this.serverName,
                                b, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                    }
                } catch (KeeperException e) {
                    logger.error(e);
                }
            }

            connectedSignal.await();
        } catch (KeeperException ke) {
            logger.error(ke);
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
            zk.setData(path, data, zk.exists(path, true).getVersion());
        } catch (KeeperException | InterruptedException e) {
            logger.error("Set up nodes " + e);
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

    public static void main(String[] args) throws IOException {
        try {
            System.out.println("Arguments: ");
            new LogSetup("logs/server.log", Level.ALL);
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

    private IECSNode parseByteArrayToNode(byte[] data){
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
}
