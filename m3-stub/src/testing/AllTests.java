package testing;

import java.io.IOException;

import app_kvECS.ECSClient;
import org.apache.log4j.Level;

import app_kvServer.KVServer;
import junit.framework.Test;
import junit.framework.TestSuite;
import logger.LogSetup;


public class AllTests {

	public static KVServer server_FIFO;
	public static KVServer server_LRU;
	public static KVServer server_LFU;

	static {
		try {
			new LogSetup("logs/testing/test.log", Level.ERROR);
			server_FIFO = new KVServer(50010, 10, "FIFO");
			server_FIFO.clearStorage();
			server_FIFO.run();
			server_LRU = new KVServer(50020, 10, "LRU");
			server_LRU.clearStorage();
			server_LRU.run();
			server_LFU = new KVServer(50030, 10, "LFU");
			server_LFU.clearStorage();
			server_LFU.run();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	public static Test suite() {
		TestSuite clientSuite = new TestSuite("Basic Storage ServerTest-Suite");
		clientSuite.addTestSuite(ConnectionTest.class);
		clientSuite.addTestSuite(InteractionTest.class);
		clientSuite.addTestSuite(AdditionalTest.class);
		clientSuite.addTestSuite(ECSTest.class);
		clientSuite.addTestSuite(ReplicaTest.class);
		return clientSuite;
	}
	
}
