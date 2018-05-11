package testing;

import org.junit.Test;

import junit.framework.TestCase;

import client.KVStore;
import common.KVMessage;
import common.KVMessage.StatusType;
import client.ClientWrapper;

public class AdditionalTest extends TestCase {

    private KVStore kvClient;
    private KVStore kvClient_LRU;
    private KVStore kvClient_LFU;

    public void setUp() {
        kvClient = new KVStore("localhost", 50010);

        try {
            kvClient.connect();
        } catch (Exception e) {

        }
    }

    public void tearDown() {
        kvClient.disconnect();
    }

    private String fakeLongValue() {
        String bigValue = "";
        for (int i = 0; i <= 121 * 1024; i += 32) {
            bigValue += "abcdefghijklmnopqrstuvwxyz,./;'[]";
        }
        return bigValue;
    }

    @Test
    public void testPutLongKey() {
        String key = "ThisKeyHasExceededTheMaxLength";
        String value = "putLongKey";
        KVMessage response = null;
        Exception ex = null;

        try {
            response = kvClient.put(key, value);
        } catch (Exception e) {
            ex = e;
        }
        assertTrue(ex == null && response.getStatus() == StatusType.PUT_ERROR);
    }

    @Test
    public void testPutLongValue() {
        String key = "putLongValue";
        String value = fakeLongValue();

        KVMessage response = null;
        Exception ex = null;

        try {
            response = kvClient.put(key, value);
        } catch (Exception e) {
            ex = e;
        }
        assertTrue(ex == null && response.getStatus() == StatusType.PUT_ERROR);
    }

    @Test
    public void testUpdateLongValue() {
        String key = "updateLongValue";
        String value1 = "shortValue";
        String value2 = fakeLongValue();

        KVMessage response = null;
        Exception ex = null;

        try {
            kvClient.put(key, value1);
            response = kvClient.put(key, value2);
        } catch (Exception e) {
            ex = e;
        }
        assertTrue(ex == null && response.getStatus() == StatusType.PUT_ERROR);
    }

    @Test
    public void testGetLongKey() {
        String key = "ThisKeyHasExceededTheMaxLength";
        KVMessage response = null;
        Exception ex = null;

        try {
            response = kvClient.get(key);
        } catch (Exception e) {
            ex = e;
        }
        assertTrue(ex == null && response.getStatus() == StatusType.GET_ERROR);
    }

    @Test
    public void testDeleteUnsetValue() {
        String key = "deleteUnsetValue";
        KVMessage response = null;
        Exception ex = null;

        try {
            response = kvClient.put(key, "null");
        } catch (Exception e) {
            ex = e;
        }
        assertTrue(ex == null && response.getStatus() == StatusType.DELETE_ERROR);
    }

    @Test
    public void testMultipleClientDifferentKey() {
        int numGetClients = 3;
        ClientWrapper MutlipleClients[] = new ClientWrapper[numGetClients];
        Thread threads[] = new Thread[numGetClients];
        int i = 0;
        for (i = 0; i < numGetClients; i++) {
            MutlipleClients[i] = new ClientWrapper("localhost", 50010, "" + i, 50, ClientWrapper.TestType.PutGetDifferentKey);
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
        assertTrue(true);
    }

    @Test
    public void testMultipleClientUpdateSameKey() {
        int ClientNumbers = 3;
        ClientWrapper MutlipleClients[] = new ClientWrapper[ClientNumbers];
        Thread threads[] = new Thread[ClientNumbers];
        int i = 0;
        for (i = 0; i < ClientNumbers; i++) {
            MutlipleClients[i] = new ClientWrapper("localhost", 50010, "" + i, 50, ClientWrapper.TestType.PutSameKey);
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

        MutlipleClients = new ClientWrapper[ClientNumbers];
        for (i = 0; i < ClientNumbers; i++) {
            MutlipleClients[i] = new ClientWrapper("localhost", 50010, "" + i, 50, ClientWrapper.TestType.UpdateGetSameKey);
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
            // any of the clients failed
            if (MutlipleClients[i].Fail) {
                assertTrue(false);
            }
        }
        assertTrue(true);
    }

    @Test
    public void testFIFOcache() {
        String key = "Key_";
        String value = "Value_";
        KVMessage response = null;
        boolean incache = true;
        Exception ex = null;

        try {
            for (int i = 0; i < 11; i++) {
                response = kvClient.put(key + i, value + i);
            }
            incache = AllTests.server_FIFO.inCache(key + 0);
        } catch (Exception e) {
            ex = e;
        }
        assertTrue(ex == null && !incache &&
                response.getStatus() == StatusType.PUT_SUCCESS);
    }

    @Test
    public void testLRUcache() {
        kvClient_LRU = new KVStore("localhost", 50020);

        try {
            kvClient_LRU.connect();
        } catch (Exception e) {
        }

        String key = "Key_";
        String value = "Value_";
        KVMessage response = null;
        boolean incache = true;
        Exception ex = null;

        try {
            for (int i = 0; i < 10; i++) {
                response = kvClient_LRU.put(key + i, value + i);
            }
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < i; j++) {
                    response = kvClient_LRU.get(key + i);
                }
            }
            response = kvClient_LRU.put(key + 10, value + 10);
            incache = AllTests.server_LRU.inCache(key + 0);
        } catch (Exception e) {
            ex = e;
        }
        assertTrue(ex == null && !incache &&
                response.getStatus() == StatusType.PUT_SUCCESS);
        kvClient_LRU.disconnect();
    }

    @Test
    public void testLFUcache() {
        kvClient_LFU = new KVStore("localhost", 50030);

        try {
            kvClient_LFU.connect();
        } catch (Exception e) {
        }

        String key = "Key_";
        String value = "Value_";
        KVMessage response = null;
        boolean incache = true;
        Exception ex = null;

        try {
            for (int i = 0; i < 10; i++) {
                response = kvClient_LFU.put(key + i, value + i);
            }
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < i; j++) {
                    response = kvClient_LFU.get(key + i);
                }
            }
            response = kvClient_LFU.put(key + 10, value + 10);
            response = kvClient_LFU.put(key + 11, value + 11);
            incache = AllTests.server_LFU.inCache(key + 10);
        } catch (Exception e) {
            ex = e;
        }
        assertTrue(ex == null && !incache &&
                response.getStatus() == StatusType.PUT_SUCCESS);
        kvClient_LFU.disconnect();
    }

    @Test
    public void testCachePerformanceWithDifferentCacheSize() {
        PerformanceTestHelper pth1 = new PerformanceTestHelper(50100, 10, "FIFO");
        PerformanceTestHelper pth2 = new PerformanceTestHelper(50150, 50, "FIFO");

        long curr_time = System.currentTimeMillis();
        pth1.FIFOPut50Get50Performance();
        long finish_time = System.currentTimeMillis();
        long result1 = finish_time - curr_time;

        curr_time = System.currentTimeMillis();
        pth2.FIFOPut50Get50Performance();
        finish_time = System.currentTimeMillis();
        long result2 = finish_time - curr_time;

        System.out.println("cache size 10 time: " + result1 + "ms cache size 50 time: " + result2 + "ms");
    }

    @Test
    public void testCachePerformanceWithGetPutRatio() {
        PerformanceTestHelper pth = new PerformanceTestHelper(50200, 10, "FIFO");

        long curr_time = System.currentTimeMillis();
        pth.FIFOPut50Get50Performance();
        long finish_time = System.currentTimeMillis();
        long result1 = finish_time - curr_time;

        curr_time = System.currentTimeMillis();
        pth.FIFOPut20Get80Performance();
        finish_time = System.currentTimeMillis();
        long result2 = finish_time - curr_time;

        curr_time = System.currentTimeMillis();
        pth.FIFOPut80Get20Performance();
        finish_time = System.currentTimeMillis();
        long result3 = finish_time - curr_time;

        System.out.println("Put/Get Ratio 1/1 time: " + result1 +
                "ms Put/Get Ratio 1/4 time: " + result2 + "ms Put/Get Ratio 4/1 time: " + result3 + "ms");
    }

    @Test
    public void testCachePerformanceWithCacheStrategy() {
        PerformanceTestHelper pth = new PerformanceTestHelper(50300, 10, "ALL");

        long curr_time = System.currentTimeMillis();
        pth.FIFOPut50Get50Performance();
        long finish_time = System.currentTimeMillis();
        long result1 = finish_time - curr_time;

        curr_time = System.currentTimeMillis();
        pth.LRUPut50Get50Performance();
        finish_time = System.currentTimeMillis();
        long result2 = finish_time - curr_time;

        curr_time = System.currentTimeMillis();
        pth.LFUPut50Get50Performance();
        finish_time = System.currentTimeMillis();
        long result3 = finish_time - curr_time;

        System.out.println("FIFO time: " + result1 +
                "ms LRU time: " + result2 + "ms LFU time: " + result3 + "ms");
    }
}
