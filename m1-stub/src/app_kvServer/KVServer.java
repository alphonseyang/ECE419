package app_kvServer;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import logger.LogSetup;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import common.communication.KVCommunication;
import persistent_storage.StorageManager;
import app_kvServer.kvCache.cacheController;


public class KVServer implements IKVServer{

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
    
	/**
	 * Start KV Server at given port
	 * @param port given port for storage server to operate
	 * @param cacheSize specifies how many key-value pairs the server is allowed
	 *           to keep in-memory
	 * @param strategy specifies the cache replacement strategy in case the cache
	 *           is full and there is a GET- or PUT-request on a key that is
	 *           currently not contained in the cache. Options are "FIFO", "LRU",
	 *           and "LFU".
	 */
	public KVServer(int port, int cacheSize, String strategy) {
		this.port = port;
		this.cacheSize = cacheSize;
		this.strategy = strategy;
		threadList = new ArrayList<Thread>();
		serverThread = null;
		this.DataManager= new StorageManager(port);
        this.DataManager.initilizateStoreManager();
		this.Cache = new cacheController(cacheSize, strategy);
	}

	@Override
	public int getPort(){
		return this.serverSocket.getLocalPort();
	}

	@Override
    public String getHostname(){
		if (serverSocket != null)
			return serverSocket.getInetAddress().getHostName();
		else
			return null;
	}

	@Override
    public CacheStrategy getCacheStrategy(){
		switch (this.strategy){
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
    public int getCacheSize(){
		return this.cacheSize;
	}

	@Override
    public boolean inStorage(String key){
		String result=null;
		try {
			result=DataManager.GetKV(key);

		}catch (Exception e){
			logger.error(e);
		}

		if (result==null)
			return false;
		else
			return true;
	}

	@Override
    public boolean inCache(String key){
		if (Cache.isHascache()) {
            return Cache.inCache(key);
        }
        return false;
	}

	@Override
    public String getKV(String key) throws Exception{
		//StorageManager test= new StorageManager(key,null,null);
		String result=null;
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


		}catch (Exception e){
			logger.error(e);
			throw e;
		}


		return result;
	}


	@Override
    public void putKV(String key, String value) throws Exception{
		//If key exist in the look up table, we should do an update command
		DataManager.PutKV(key,value);
		// Put KV into cache.
        if (Cache.isHascache()) {
            Cache.set(key, value);
        }
	}
	
	public void deleteKV(String key) throws Exception{
		DataManager.DeleteKV(key);
		if (Cache.isHascache()) {
            Cache.delete(key);
        }
	}

	@Override
    public void clearCache(){
		if (Cache.isHascache()) {
            Cache.clearCache();
        }
	}

	@Override
    public void clearStorage(){
		clearCache();
		DataManager.clearStorage();
	}

	@Override
    public void kill(){
		running = false;
        try {
			serverSocket.close();
		} catch (IOException e) {
			logger.error("Error! " +
					"Unable to close socket on port: " + port, e);
		}
	}

	@Override
    public void close(){
		running = false;
        try {
            for (int i = 0; i < threadList.size(); i++){
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
            if(e instanceof BindException){
            	logger.error("Port " + port + " is already bound!");
            }
            return false;
        }
    }
	
	public void run() {
		new KVServerWrapper(this);
    }
	
	public void loopForConnection(){
		running = initializeKVServer();

        if(serverSocket != null) {
	        while(isRunning()){
	            try {
	                Socket client = serverSocket.accept();
					KVCommunication communicator =
	                		new KVCommunication(client, this);
	                Thread t = new Thread(communicator);
	                t.start();
	                threadList.add(t);
	                
	                logger.info("Connected to " 
	                		+ client.getInetAddress().getHostName() 
	                		+  " on port " + client.getPort());
	            } catch (IOException e) {
	            	logger.error("Error! " +
	            			"Unable to establish connection. \n", e);
	            }
	        }
        }
        logger.info("Server stopped.");
	}
	
	public static void main(String[] args) throws IOException {
    	try {
    		new LogSetup("logs/server.log", Level.ALL);
			if(args.length != 3) {
				logger.error("Error! Invalid number of arguments!");
				logger.error("Usage: Server <port> <cacheSize> <strategy>!");
			} else {
				int port = Integer.parseInt(args[0]);
				int cacheSize = Integer.parseInt(args[1]);
				String strategy = args[2];
				new KVServer(port, cacheSize, strategy).run();
			}
		} catch (IOException e) {
			logger.error("Error! Unable to initialize logger!");
			e.printStackTrace();
			System.exit(1);
		}
    }
}
