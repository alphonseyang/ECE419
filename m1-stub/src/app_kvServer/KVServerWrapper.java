package app_kvServer;

import app_kvServer.KVServer;

public class KVServerWrapper implements Runnable{
	private KVServer server;
	
	public KVServerWrapper(KVServer server){
		this.server = server;
		new Thread(this).start();
	}
	
	public void run(){
		server.loopForConnection();
	}
}
