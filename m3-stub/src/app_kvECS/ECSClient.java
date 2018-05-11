package app_kvECS;

import java.io.*;
import java.net.InetAddress;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.math.BigInteger;
import java.util.concurrent.CountDownLatch;


import logger.LogSetup;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.zookeeper.*;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.data.Stat;

import ecs.IECSNode.ECSNodeFlag;
import ecs.IECSNode;
import ecs.ECSNode;
import common.datatypes.MetaData;
import common.datatypes.MD5;
import app_kvServer.KVServer.ServerStateType;
import app_kvServer.KVServer;

public class ECSClient implements IECSClient {

    private static Logger logger = Logger.getRootLogger();
    private BufferedReader stdin;
    private Path configFile;
    private TreeMap<BigInteger, IECSNode> hashRing;
    private TreeMap<BigInteger, IECSNode> metaData;
    private HashMap<BigInteger, ServerStateType> serverStates;
    private boolean stop;
    private ZooKeeper zk;
    private int serverCount = 0;


    private String sshHost = null;
    private CountDownLatch connectedSignal;
    private static String zkServerScript = "zookeeper-3.4.11/bin/zkServer.sh";
    private static String sshScript = "startScript.sh";
	private int zkPort;
	private int ipaddress;
	private Process zkProcess;

	public ECSClient(String zkHostname, int zkPort, String configFileLocation){
		try {
            logger.debug("Current Directory: " + System.getProperty("user.dir"));
            String dir = System.getProperty("user.dir");
            this.configFile = Paths.get(configFileLocation);
            this.hashRing = new TreeMap<>();
            this.serverStates = new HashMap<>();
            this.metaData = new TreeMap<>();
            this.stop = false;
			this.zkPort = zkPort;

            ProcessBuilder pd = new ProcessBuilder(dir+"/"+zkServerScript, "start");
            zkProcess = pd.start();
            connectedSignal = new CountDownLatch(1);
            this.zk = new ZooKeeper(zkHostname, 3000000, new Watcher() {
                @Override
                public void process(WatchedEvent we) {
                    if (we.getState() == KeeperState.SyncConnected) {
                        connectedSignal.countDown();
                    }
                }
            });
            connectedSignal.await();
            updateHashRingFromFile();
            clearNodes();

            // Create AwaitNode in zookeeper.
            if (zk != null) {
                try {
                    String path = "/AwaitNode";
                    Stat s = zk.exists(path, true);

                    if (s == null) {
                        zk.create(path, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE,
                                CreateMode.PERSISTENT);
                    }

                    s = zk.exists("/CrashDetector", true);
                    if (s == null){
                        MetaData temp = new MetaData(null, null, 0, null, null);
                        zk.create("/CrashDetector", generateByteArray(temp), ZooDefs.Ids.OPEN_ACL_UNSAFE,
                                CreateMode.PERSISTENT);
                    }
                } catch (KeeperException e) {
                    logger.error("Await node creation " + e);
                }
            }

            // start waiting for crashed server notification
            processServerCrash();
        } catch (IOException | InterruptedException e) {
            logger.error(e);
        }
	}

    public ECSClient(int zkPort, String zkHostname){
        try {
            logger.debug("Current Directory: " + System.getProperty("user.dir"));
            String dir = System.getProperty("user.dir");
            this.configFile = Paths.get("ecs.config");
            this.hashRing = new TreeMap<>();
            this.serverStates = new HashMap<>();
            this.metaData = new TreeMap<>();
            this.stop = false;
            this.zkPort = zkPort;

            ProcessBuilder pd = new ProcessBuilder(dir+"/"+zkServerScript, "start");
            zkProcess = pd.start();
            connectedSignal = new CountDownLatch(1);
            this.zk = new ZooKeeper(zkHostname, 3000000, new Watcher() {
                @Override
                public void process(WatchedEvent we) {
                    if (we.getState() == KeeperState.SyncConnected) {
                        connectedSignal.countDown();
                    }
                }
            });
            connectedSignal.await();
            updateHashRingFromFile();
            clearNodes();

            // Create AwaitNode in zookeeper.
            if (zk != null) {
                try {
                    String path = "/AwaitNode";
                    Stat s = zk.exists(path, true);

                    if (s == null) {
                        zk.create(path, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE,
                                CreateMode.PERSISTENT);
                    }
                } catch (KeeperException e) {
                    logger.error("Await node creation " + e);
                }
            }
        } catch (IOException | InterruptedException e) {
            logger.error(e);
        }
    }

    @Override
    public boolean start() {
        return storageServiceActions("start");
    }

    @Override
    public boolean stop() {
        return storageServiceActions("stop");
    }

    @Override
    public boolean shutdown() {
        return storageServiceActions("shutdown");
    }

    @Override
    public IECSNode addNode(String cacheStrategy, int cacheSize) {
        IECSNode node = null;
        try {
            float prev = System.nanoTime();
            BigInteger nodeKey = null;
            for (Map.Entry<BigInteger, IECSNode> entry : hashRing.entrySet()) {
                if (serverStates.get(entry.getKey()) == ServerStateType.IDLE) {
                    IECSNode temp = entry.getValue();
                    temp.setCacheInfo(cacheSize, cacheStrategy);
                    metaData.put(entry.getKey(), temp);
                    nodeKey = entry.getKey();
                    break;
                }
            }
            updateMetaDataHashRange();
            TreeMap<BigInteger, MetaData> metadataForServers = setupMetaDataForNodes();

            // create node for new metadata node
            node = metaData.get(nodeKey);
            node.setMetaData(metadataForServers);
            node.setFlag(ECSNodeFlag.STOP);
            metaData.put(nodeKey, node);
            byte[] data = generateByteArray(node);
            String path = "/" + node.getNodeName();
            zk.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            // start server, use SSH to replace
            sshStartServer(node.getNodeName());
            //new KVServer(node.getNodeName(), "localhost", 2181);
            serverStates.put(nodeKey, ServerStateType.STOPPED);
            awaitNodes(1, 300000);

            // update the metadata for changed node
            // find the node that its hash range changes
            // update that node metadata and wait for transfer complete
            Map.Entry<BigInteger, IECSNode> changed = findNodeToChange(nodeKey);
            node = changed.getValue();
            node.setFlag(ECSNodeFlag.KV_TRANSFER);
            node.setMetaData(metadataForServers);
            metaData.put(changed.getKey(), node);
            data = generateByteArray(node);
            path = "/" + node.getNodeName();
            if (zk.exists(path, true) != null){
                zk.setData(path, data, zk.exists(path, true).getVersion());
                waitForMigration(path);
            }
            float cur = System.nanoTime();
            logger.info("It takes " + (cur-prev)/1000000);
            updateStorageServersMetadata(metadataForServers);
        } catch (KeeperException | InterruptedException e) {
            logger.error("Set up nodes " + e);
        } catch (Exception e) {
            logger.error("Add node await node exception " + e);
        }
        return node;
    }

    @Override
    public Collection<IECSNode> addNodes(int count, String cacheStrategy, int cacheSize) {
        ArrayList<IECSNode> nodes = new ArrayList<>();
        int i = 0;
        for (Map.Entry<BigInteger, IECSNode> entry : metaData.entrySet()) {
            if (i < count) {
                //start server
                sshStartServer(entry.getValue().getNodeName());
                //new KVServer(entry.getValue().getNodeName(), "localhost", 2181);
                i++;
            } else {
                break;
            }
        }
        return nodes;
    }

    @Override
    public Collection<IECSNode> setupNodes(int count, String cacheStrategy, int cacheSize) {
        ArrayList<IECSNode> nodes = new ArrayList<>();
        int i = 0;
        try {
            for (Map.Entry<BigInteger, IECSNode> entry : hashRing.entrySet()) {
                if (i < count && serverStates.get(entry.getKey()) == ServerStateType.IDLE) {
                    IECSNode node = entry.getValue();
                    node.setCacheInfo(cacheSize, cacheStrategy);
                    metaData.put(entry.getKey(), node);
                    i++;
                } else {
                    break;
                }
            }
            updateMetaDataHashRange();
            TreeMap<BigInteger, MetaData> metadataForServers = setupMetaDataForNodes();
            for (Map.Entry<BigInteger, IECSNode> entry : metaData.entrySet()) {
                // store the metadata to each available node
                IECSNode newNode = entry.getValue();
                newNode.setMetaData(metadataForServers);
                metaData.put(entry.getKey(), newNode);

                // add nodes to ZooKeeper
                IECSNode node = metaData.get(entry.getKey());
                byte[] data = generateByteArray(node);
                String path = "/" + node.getNodeName();
                zk.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                serverStates.put(entry.getKey(), ServerStateType.STOPPED);
            }
        } catch (KeeperException | InterruptedException e) {
            logger.error("Set up nodes " + e);
        }
        return nodes;
    }

    @Override
    public boolean awaitNodes(int count, int timeout) throws Exception {
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeout) {
            List<String> list = zk.getChildren("/AwaitNode", true);
            if (list.size() - serverCount == count) {
                serverCount += count;
                setupReplica();
                return true;
            }
        }

        return false;
    }

    public void setupReplica() {
	    try {
            for (Map.Entry<BigInteger, IECSNode> entry : metaData.entrySet()) {
                IECSNode node = entry.getValue();
                node.setFlag(ECSNodeFlag.START_REPLICA);

                byte[] data = generateByteArray(node);

                String path = "/" + node.getNodeName();

                if (zk.exists(path, true) != null) {
                    zk.setData(path, data, zk.exists(path, true).getVersion());
                }
            }
        } catch (KeeperException | InterruptedException e) {
            logger.error("Remove nodes " + e);
        }
    }

    @Override
    public boolean removeNodes(Collection<String> nodeNames) {
        try {
            float prev = System.nanoTime();
            for (String str : nodeNames) {
                // prepare node
                BigInteger key = MD5.HashInBI(str);
                IECSNode node = metaData.get(key);
                node.setFlag(ECSNodeFlag.KV_TRANSFER);

                // remove node and update metadata information
                metaData.remove(key);
                updateMetaDataHashRange();
                TreeMap<BigInteger, MetaData> metadataForServers = setupMetaDataForNodes();

                // update metadata information for removed node
                node.setMetaData(metadataForServers);
                byte[] data = generateByteArray(node);
                String path = "/" + node.getNodeName();
                if (zk.exists(path, true) != null) {
                    zk.setData(path, data, zk.exists(path, true).getVersion());
                    waitForMigration(path);
                }

                // delete node and update information
                serverStates.put(key, ServerStateType.IDLE);
                zk.delete(path, -1);
                zk.delete("/AwaitNode" + path, -1);
                serverCount--;

                updateStorageServersMetadata(metadataForServers);
            }
            float cur = System.nanoTime();
            logger.info("It takes " + (cur-prev)/1000000);
            return true;
        } catch (KeeperException | InterruptedException | NoSuchAlgorithmException e) {
            logger.error("Remove nodes " + e);
            return false;
        }
    }

    @Override
    public Map<String, IECSNode> getNodes() {
        Map<String, IECSNode> result = new HashMap<>();
        for (Map.Entry<BigInteger, IECSNode> entry : metaData.entrySet()) {
            IECSNode node = entry.getValue();
            result.put(node.getNodeName(), node);
        }
        return result;
    }

    @Override
    public IECSNode getNodeByKey(String Key) {
        return metaData.get(Key);
    }

    public void run() {
        while (!stop) {
            stdin = new BufferedReader(new InputStreamReader(System.in));
            System.out.print("ECSClient> ");

            try {
                String cmdLine = stdin.readLine();
                this.handleCommand(cmdLine);
            } catch (IOException e) {
                stop = true;
                logger.error("Client does not respond - Application terminated ");
            }
        }
    }

    private void updateHashRingFromFile() {
        try {
            ArrayList<String> fileContent = new ArrayList<>(Files.readAllLines(configFile,
                    StandardCharsets.UTF_8));

            for (int i = 0; i < fileContent.size(); i++) {
                String[] line = fileContent.get(i).split("\\s+");
                BigInteger hashValue = MD5.HashInBI(line[0] + ":" + line[1] + ":" + line[2]);

                // store the hash value - metadata pairs
                hashRing.put(hashValue, new ECSNode(line[0] + ":" + line[1] + ":" + line[2], line[1], Integer.parseInt(line[2])));
                serverStates.put(hashValue, ServerStateType.IDLE);
            }
        } catch (IOException ex1) {
            logger.error("Error! Unable to read config file for ECS");
        } catch (NoSuchAlgorithmException ex2) {
            logger.error("Error! Unable to create hash ring from ECS config file");
        }
    }

    private void handleCommand(String cmdLine) {
        String[] tokens = cmdLine.split("\\s+");

        if (tokens[0].equals("quit")) {
            stop = true;
            shutdown();
            logger.info("Service shutdown");
        } else if (tokens[0].equals("init")) {
            // initiate the storage servers
            if (sshHost == null) {
                logger.info("Please provide ssh host name before initialization");
                logger.info("Format: name <your ug machine number or localhost>");
            } else {
                if (tokens.length == 4) {
                    initiateStorageService(Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2]), tokens[3]);
                } else {
                    logger.error("Expect a total of 5 arguments");
                }
            }
        } else if (tokens[0].equals("start")) {
            // start the storage servers
            if (tokens.length == 1) {
                start();
                logger.info("Server started");
            } else {
                logger.error("Expect a total of 1 arguments");
            }
        } else if (tokens[0].equals("stop")) {
            // stop the storage servers
            if (tokens.length == 1) {
                stop();
                logger.info("Server stopped");
            } else {
                logger.error("Expect a total of 1 arguments");
            }
        } else if (tokens[0].equals("add")) {
            // add node to the storage service
            if (tokens.length == 3) {
                IECSNode node = addNode(tokens[1], Integer.parseInt(tokens[2]));
            } else if (tokens.length == 4) {
                Collection<IECSNode> nodes = addNodes(Integer.parseInt(tokens[1]), tokens[2], Integer.parseInt(tokens[3]));
            }
        } else if (tokens[0].equals("remove")) {
            if (tokens.length > 1) {
                ArrayList<String> nodesName = new ArrayList<String>();
                for (int i = 1; i < tokens.length; i++) {
                    nodesName.add(tokens[i]);
                }
                removeNodes(nodesName);
            } else {
                logger.error("Expect more than 1 arguments");
            }
        } else if (tokens[0].equals("name")) {
            if (tokens.length == 2) {
                if (tokens[1].equals("localhost")) {
                    sshHost = "localhost";
                }
                else {
                    //int ugnum = Integer.parseInt(tokens[1]);
                    ipaddress = Integer.parseInt(tokens[1]);
                    sshHost = "ug" + tokens[1];
                }
            } else {
                logger.error("Please provide valid ug machine number");
            }
        }else if (tokens[0].equals("debug")){
            logger.setLevel(Level.DEBUG);
            logger.info("Logger level set to debug");
        }
        else if (tokens[0].equals("info")){
            logger.setLevel(Level.INFO);
            logger.info("Logger level set to info");
        } else {
            logger.error("Please enter the correct command!");
        }
    }

    public Collection<IECSNode> initiateStorageService(int numOfServers, int cacheSize, String cacheStrategy) {
        setupNodes(numOfServers, cacheStrategy, cacheSize);
        Collection<IECSNode> nodes = addNodes(numOfServers, cacheStrategy, cacheSize);
        try {
            awaitNodes(numOfServers, 30000);
        } catch (Exception e) {
            logger.error(e);
        }
        return nodes;
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

    private void updateMetaDataHashRange() {
        BigInteger prev = null;
        for (Map.Entry<BigInteger, IECSNode> entry : metaData.entrySet()) {
            if (prev != null) {
                IECSNode node = entry.getValue();
                node.setHashRanges(prev, entry.getKey());
                metaData.put(entry.getKey(), node);
            }
            prev = entry.getKey();
        }
        if (metaData.size() > 0) {
            IECSNode node = metaData.get(metaData.firstKey());
            node.setHashRanges(prev, metaData.firstKey());
            metaData.put(metaData.firstKey(), node);
        }
    }

    private TreeMap<BigInteger, MetaData> setupMetaDataForNodes() {
        TreeMap<BigInteger, MetaData> map = new TreeMap<>();
        for (Map.Entry<BigInteger, IECSNode> entry : metaData.entrySet()) {
            IECSNode node = entry.getValue();
            map.put(entry.getKey(),
                    new MetaData(node.getNodeName(), node.getNodeHost(), node.getNodePort(),
                            node.getNodeHashRange()[0], node.getNodeHashRange()[1]));
        }
        return map;
    }

    private void clearNodes() {
        try {
            Stat s = zk.exists("/AwaitNode", true);
            if (s != null) {
                List<String> list = zk.getChildren("/AwaitNode", true);

                for (String nodeName : list) {
                    zk.delete("/AwaitNode/" + nodeName, -1);
                }

                while (true){
                    list = zk.getChildren("/AwaitNode", true);
                    if (list.size() == 0)
                        break;
                }

                zk.delete("/AwaitNode", -1);
            }

            s = zk.exists("/CrashDetector", true);
            if (s != null){
                zk.delete("/CrashDetector", -1);
            }

            if (hashRing != null) {
                Set set = hashRing.entrySet();
                Iterator i = set.iterator();

                while (i.hasNext()) {
                    Map.Entry<BigInteger, ECSNode> me = (Map.Entry<BigInteger, ECSNode>) i.next();
                    IECSNode node = me.getValue();
                    String znodeName = "/" + node.getNodeName();
                    try {
                        if (zk.exists(znodeName, true) != null)
                            zk.delete(znodeName, -1);
                    } catch (KeeperException ke) {
                        logger.error("Clear Client nodes" + ke);
                    }
                }
            }
        } catch (KeeperException | InterruptedException ie) {
            logger.error("Clear nodes " + ie);
        }
    }

    private boolean storageServiceActions(String action) {
        ECSNodeFlag flag = null;
        String log = null;
        ServerStateType serverState = null;
        switch (action) {
            case "start":
                flag = ECSNodeFlag.START;
                log = "ECS Start ";
                serverState = ServerStateType.STARTED;
                break;
            case "stop":
                flag = ECSNodeFlag.STOP;
                log = "ECS Stop ";
                serverState = ServerStateType.STOPPED;
                break;
            case "shutdown":
                flag = ECSNodeFlag.SHUT_DOWN;
                log = "ECS Shut down ";
                serverState = ServerStateType.SHUT_DOWN;
                break;
            default:
                flag = ECSNodeFlag.ERROR;
                log = "ECS Action error ";
                serverState = ServerStateType.ERROR;
                break;
        }
        try {
            for (Map.Entry<BigInteger, IECSNode> entry : metaData.entrySet()) {
                serverStates.put(entry.getKey(), serverState);
                IECSNode node = entry.getValue();
                node.setFlag(flag);
                metaData.put(entry.getKey(), node);
                String path = "/" + node.getNodeName();
                if (zk.exists(path, true) != null)
                    zk.setData(path, generateByteArray(node), zk.exists(path, true).getVersion());
            }
        } catch (InterruptedException ie) {
            logger.error(log + ie);
            return false;
        } catch (KeeperException ke) {
            logger.error(log + ke);
            return false;
        }
        return true;
    }

    private Map.Entry<BigInteger, IECSNode> findNodeToChange(BigInteger newNodeKey) {
        if (metaData.higherEntry(newNodeKey) != null)
            return metaData.higherEntry(newNodeKey);
        else
            return metaData.firstEntry();
    }

    private void sshStartServer(String nodeName) {
        String dir = System.getProperty("user.dir");

        String[] command = {dir + "/" + sshScript, nodeName, sshHost, Integer.toString(zkPort)};

        try {
            ProcessBuilder pd2 = new ProcessBuilder(command).inheritIO();
            Process p2 = pd2.start();
        } catch (IOException e) {
            System.out.println("Unable to start process builder " + e);
        }
    }

    private void waitForMigration(String path) {
        try {
            while(true) {
                if (zk.exists(path, true) !=  null) {
                    byte[] got = zk.getData(path, true, null);
                    Object obj = generateObjectFromByteArray(got);
                    IECSNode node = (IECSNode) obj;
                    if (node.getFlag() == ECSNodeFlag.TRANSFER_FINISH) {
                        break;
                    }
                }
            }
        } catch (InterruptedException ie) {
            logger.error("Wait for error " + ie);
        } catch (KeeperException ke) {
            logger.error("Wait for error " + ke);
        }

    }

    private void processServerCrash(){
	    try {
            if (zk.exists("/CrashDetector", true) != null) {
                byte[] data = zk.getData("/CrashDetector", new Watcher() {
                    @Override
                    public void process(WatchedEvent we) {
                        processServerCrash();
                    }
                }, null);
                MetaData crashedInfo = (MetaData)generateObjectFromByteArray(data);
                if (crashedInfo.getHost() != null){
                    // remove that from available servers structure
                    // update metadata
                    BigInteger key = MD5.HashInBI(crashedInfo.getName());
                    metaData.remove(key);
                    updateMetaDataHashRange();
                    TreeMap<BigInteger, MetaData> metadataForServers = setupMetaDataForNodes();
                    String path = "/" + crashedInfo.getName();
                    serverStates.put(key, ServerStateType.IDLE);
                    zk.delete(path, -1);
                    zk.delete("/AwaitNode" + path, -1);
                    serverCount--;

                    //updateStorageServersMetadata(metadataForServers);
                    // restart a new server
                    addNode(metaData.firstEntry().getValue().getCacheStrategy(), metaData.firstEntry().getValue().getCacheSize());
                }
            }
        } catch (KeeperException ke){
            logger.error("Process server crash error " + ke);
        } catch (InterruptedException ie){
	        logger.error("Process server crash error " + ie);
        } catch (NoSuchAlgorithmException nsae){
	        logger.error("Process server crash error hash error " + nsae);
        }
    }

    private Object generateObjectFromByteArray(byte[] data){
	    Object obj = null;
        ByteArrayInputStream bis = null;
        ObjectInputStream ois = null;
        try {
            bis = new ByteArrayInputStream(data);
            ois = new ObjectInputStream(bis);
            obj = ois.readObject();
        } catch (IOException ioe) {
            logger.error(ioe);
        } catch (ClassNotFoundException ce) {
            logger.error(ce);
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
        return obj;
    }

    private void updateStorageServersMetadata(TreeMap<BigInteger, MetaData> newMetaData) throws InterruptedException, KeeperException{
        // update the rest node value
        for (Map.Entry<BigInteger, IECSNode> entry : metaData.entrySet()) {
            // store the metadata to each available node
            IECSNode newNode = entry.getValue();
            newNode.setMetaData(newMetaData);
            newNode.setFlag(ECSNodeFlag.UPDATE);
            metaData.put(entry.getKey(), newNode);
            byte[] data = generateByteArray(newNode);
            String path = "/" + newNode.getNodeName();
            if (zk.exists(path, true) != null) {
                zk.setData(path, data, zk.exists(path, true).getVersion());
            }
        }
    }

    public TreeMap<BigInteger, IECSNode> getHashRing()
    {
        return this.hashRing;
    }

    public TreeMap<BigInteger, IECSNode> getMetaData()
    {
        return this.metaData;
    }

    public void addSSHName(String name){
        sshHost = name;
    }

    public ZooKeeper getZK () { return zk; }

    public int getServerCount () { return serverCount; }

    public static void main(String[] args) {
        if (args.length != 2)
            System.out.println("Wrong input args for number of servers");
        else {
            try {
                new LogSetup("logs/ecs.log", Level.INFO);
                ECSClient ecsApp = new ECSClient(args[0], Integer.parseInt(args[1]), "ecs.config");
                ecsApp.run();
                System.out.println("Done");
            } catch (IOException e) {
                System.out.println("Error! Unable to initialize logger!");
                e.printStackTrace();
                System.exit(1);
            }
        }
    }
}
