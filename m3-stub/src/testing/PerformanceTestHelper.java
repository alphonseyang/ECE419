package testing;

import app_kvServer.KVServer;

import client.KVStore;

import java.util.Random;

public class PerformanceTestHelper {
    private KVStore kvClient_FIFO;
    private KVStore kvClient_LRU;
    private KVStore kvClient_LFU;
    private KVServer performance_server_FIFO;
    private KVServer performance_server_LRU;
    private KVServer performance_server_LFU;
    private int port;

    public PerformanceTestHelper(int port, int cacheSize, String strategy) {
        this.port = port;
        switch (strategy) {
            case "FIFO":
                performance_server_FIFO = new KVServer(port, cacheSize, "FIFO");
                performance_server_FIFO.clearStorage();
                performance_server_FIFO.run();
                break;
            case "LRU":
                performance_server_LRU = new KVServer(port, cacheSize, "LRU");
                performance_server_LRU.clearStorage();
                performance_server_LRU.run();
                break;
            case "LFU":
                performance_server_LFU = new KVServer(port, cacheSize, "LFU");
                performance_server_LFU.clearStorage();
                performance_server_LFU.run();
                break;
            default:
                performance_server_FIFO = new KVServer(port, cacheSize, "FIFO");
                performance_server_FIFO.clearStorage();
                performance_server_FIFO.run();
                performance_server_LRU = new KVServer(port + 1, cacheSize, "LRU");
                performance_server_LRU.clearStorage();
                performance_server_LRU.run();
                performance_server_LFU = new KVServer(port + 2, cacheSize, "LFU");
                performance_server_LFU.clearStorage();
                performance_server_LFU.run();
                break;
        }
    }

    public void close() {
        if (performance_server_FIFO != null)
            performance_server_FIFO.close();
        if (performance_server_LRU != null)
            performance_server_LRU.close();
        if (performance_server_LFU != null)
            performance_server_LFU.close();
    }

    public void FIFOPut20Get80Performance() {
        AllTests.server_FIFO.clearStorage();
        kvClient_FIFO = new KVStore("localhost", port);

        try {
            kvClient_FIFO.connect();
        } catch (Exception e) {
        }

        Random rg = new Random(3);
        String[] values = {"value1", "value2"};
        try {
            for (int i = 0; i < 50; i++) {
                String[] keys = {"key_" + i + "_" + 0, "key_" + i + "_" + 1, "key_" + i + "_" + 2, "key_" + i + "_" + 3, "key_" + i + "_" + 4, "key_" + i + "_" + 5, "key_" + i + "_" + 6, "key_" + i + "_" + 7};
                for (int j = 0; j < 2; j++) {
                    kvClient_FIFO.put(keys[j], values[j]);
                }
            }
            for (int i = 0; i < 400; i++) {
                kvClient_FIFO.get("key_" + rg.nextInt(50) + "_" + rg.nextInt(2));
            }
        } catch (Exception e) {

        }

        kvClient_FIFO.disconnect();
    }

    public void FIFOPut50Get50Performance() {
        AllTests.server_FIFO.clearStorage();
        kvClient_FIFO = new KVStore("localhost", port);

        try {
            kvClient_FIFO.connect();
        } catch (Exception e) {
        }

        Random rg = new Random(3);
        String[] values = {"value1", "value2", "value3", "value4", "value5"};
        try {
            for (int i = 0; i < 50; i++) {
                String[] keys = {"key_" + i + "_" + 0, "key_" + i + "_" + 1, "key_" + i + "_" + 2, "key_" + i + "_" + 3, "key_" + i + "_" + 4, "key_" + i + "_" + 5, "key_" + i + "_" + 6, "key_" + i + "_" + 7};
                for (int j = 0; j < 5; j++) {
                    kvClient_FIFO.put(keys[j], values[j]);
                }
            }
            for (int i = 0; i < 250; i++) {
                kvClient_FIFO.get("key_" + rg.nextInt(50) + "_" + rg.nextInt(5));
            }
        } catch (Exception e) {

        }

        kvClient_FIFO.disconnect();
    }

    public void FIFOPut80Get20Performance() {
        AllTests.server_FIFO.clearStorage();
        kvClient_FIFO = new KVStore("localhost", port);

        try {
            kvClient_FIFO.connect();
        } catch (Exception e) {
        }

        Random rg = new Random(3);
        String[] values = {"value1", "value2", "value3", "value4", "value5", "value6", "value7", "value8"};
        try {
            for (int i = 0; i < 50; i++) {
                String[] keys = {"key_" + i + "_" + 0, "key_" + i + "_" + 1, "key_" + i + "_" + 2, "key_" + i + "_" + 3, "key_" + i + "_" + 4, "key_" + i + "_" + 5, "key_" + i + "_" + 6, "key_" + i + "_" + 7};
                for (int j = 0; j < 8; j++) {
                    kvClient_FIFO.put(keys[j], values[j]);
                }
            }
            for (int i = 0; i < 100; i++) {
                kvClient_FIFO.get("key_" + rg.nextInt(50) + "_" + rg.nextInt(8));
            }
        } catch (Exception e) {

        }

        kvClient_FIFO.disconnect();
    }

    public void LRUPut50Get50Performance() {
        AllTests.server_LRU.clearStorage();
        kvClient_LRU = new KVStore("localhost", port + 1);

        try {
            kvClient_LRU.connect();
        } catch (Exception e) {
        }


        Random rg = new Random(3);
        String[] values = {"value1", "value2", "value3", "value4", "value5"};
        try {
            for (int i = 0; i < 50; i++) {
                String[] keys = {"key_" + i + "_" + 0, "key_" + i + "_" + 1, "key_" + i + "_" + 2, "key_" + i + "_" + 3, "key_" + i + "_" + 4, "key_" + i + "_" + 5, "key_" + i + "_" + 6, "key_" + i + "_" + 7};
                for (int j = 0; j < 5; j++) {
                    kvClient_LRU.put(keys[j], values[j]);
                }
            }
            for (int i = 0; i < 250; i++) {
                kvClient_LRU.get("key_" + rg.nextInt(50) + "_" + rg.nextInt(5));
            }
        } catch (Exception e) {

        }

        kvClient_LRU.disconnect();
    }

    public void LFUPut50Get50Performance() {
        AllTests.server_LFU.clearStorage();
        kvClient_LFU = new KVStore("localhost", port + 2);

        try {
            kvClient_LFU.connect();
        } catch (Exception e) {
        }

        Random rg = new Random(3);
        String[] values = {"value1", "value2", "value3", "value4", "value5"};
        try {
            for (int i = 0; i < 100; i++) {
                String[] keys = {"key_" + i + "_" + 0, "key_" + i + "_" + 1, "key_" + i + "_" + 2, "key_" + i + "_" + 3, "key_" + i + "_" + 4, "key_" + i + "_" + 5, "key_" + i + "_" + 6, "key_" + i + "_" + 7};
                for (int j = 0; j < 5; j++) {
                    kvClient_LFU.put(keys[j], values[j]);
                }
            }
            for (int i = 0; i < 250; i++) {
                kvClient_LFU.get("key_" + rg.nextInt(50) + "_" + rg.nextInt(5));
            }
        } catch (Exception e) {

        }

        kvClient_LFU.disconnect();
    }
}
