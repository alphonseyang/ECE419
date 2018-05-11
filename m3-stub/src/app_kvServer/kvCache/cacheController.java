package app_kvServer.kvCache;

import app_kvServer.KeyValue;
import app_kvServer.kvCache.LFUCachePolicy;
import app_kvServer.kvCache.FIFOCachePolicy;
import app_kvServer.kvCache.LRUCachePolicy;

//import org.apache.log4j.Level;
import org.apache.log4j.Logger;
//import logger.LogSetup;

public class cacheController {
    private cachePolicies cache;
    private boolean hascache = false;
    private static Logger logger = Logger.getRootLogger();

    public cacheController (int cacheSize, String strategy) {
        if (strategy.equals("FIFO")) {
            hascache = true;
            cache = new FIFOCachePolicy(cacheSize);
            logger.info("Initialize " + strategy +
                    " cache with cache size: " + cacheSize);
        }
        else if (strategy.equals("LRU")) {
            hascache = true;
            cache = new LRUCachePolicy(cacheSize);
            logger.info("Initialize " + strategy +
                    " cache with cache size: " + cacheSize);
        }
        else if (strategy.equals("LFU")) {
            hascache = true;
            cache = new LFUCachePolicy(cacheSize);
            logger.info("Initialize " + strategy +
                    " cache with cache size: " + cacheSize);
        }
        else {
            hascache = false;
            cache = null;
            logger.error("Error! Cannot initialize cache strategy "
                    + strategy + " with "
                    + cacheSize + " cache size.");
            // Not any of above policy, cannot create cache.
        }
    }

    public String get(String key) {
        KeyValue KV = new KeyValue(key, null);
        try {
            if (cache != null) {
                KV = cache.readCache(key);
                if (KV != null) {
                    // Found key in cache.
                    logger.info("Found KeyValue in cache! KeyValue is: "
                        + KV.convertString());
                    return KV.getValue();
                } else { // Not found in cache, try disk.
                    // TO-DO: try to find key in disk.
                    // Handle two cases here:
                    // 1. Found in disk, write to cache.
                    // 2. Not found in disk, key does not exist in storage.
                    logger.info("Key " + key + " is not found in cache.");
                    return null;
                }
            }
            else
                logger.error("Cache does not exist.");
        }
        catch (Exception e) {
            // Error handle.
            logger.error(e);
        }
        return KV.getValue();
    }

    public void set(String key, String value) {
        KeyValue KV = new KeyValue(key, value);
        try {
            // TO-DO: write to disk.
            if (cache != null) {
                cache.writeCache(KV);
                logger.info("KeyValue " + KV.convertString() +
                        " has been stored in cache.");
            }
            else
                logger.error("Cache does not exist.");
        }
        catch (Exception e) {
            // Error Handle.
            logger.error(e);
        }
        //return KV;
    }

    public void delete(String key) {
        if (cache != null) {
            cache.deleteCache(key);
        }
        else
            logger.error("Cache does not exist.");
    }

    public boolean inCache(String key) {
        if (cache != null) {
            return cache.inCache(key);
        }
        else {
            logger.error("Cache does not exist.");
            return false;
        }
    }

    public void clearCache() {
        if (cache != null) {
            cache.clearCache();
        }
        else
            logger.error("Cache does not exist.");
    }

    public boolean isHascache() {
        return hascache;
    }

    public static void main(String [] args) {
        cacheController cacheCont = new cacheController(5, "FIFO");

        try {
            KeyValue KV1 = new KeyValue("Key1", "Value1");
            KeyValue KV2 = new KeyValue("Key2", "Value2");
            KeyValue KV3 = new KeyValue("Key3", "Value3");
            KeyValue KV4 = new KeyValue("Key4", "Value4");
            KeyValue KV5 = new KeyValue("Key5", "Value5");
            KeyValue KV6 = new KeyValue("Key6", "Value6");

            cacheCont.set(KV1.getKey(), KV1.getValue());
            System.out.println("Max cache size is: " + cacheCont.cache.getMaxcacheSize());
            System.out.println("Current cache size is: " + cacheCont.cache.getcurrentcacheSize());
            cacheCont.set(KV2.getKey(), KV2.getValue());
            System.out.println("Max cache size is: " + cacheCont.cache.getMaxcacheSize());
            System.out.println("Current cache size is: " + cacheCont.cache.getcurrentcacheSize());
            cacheCont.set(KV3.getKey(), KV3.getValue());
            System.out.println("Max cache size is: " + cacheCont.cache.getMaxcacheSize());
            System.out.println("Current cache size is: " + cacheCont.cache.getcurrentcacheSize());
            cacheCont.set(KV4.getKey(), KV4.getValue());
            System.out.println("Max cache size is: " + cacheCont.cache.getMaxcacheSize());
            System.out.println("Current cache size is: " + cacheCont.cache.getcurrentcacheSize());
            cacheCont.set(KV5.getKey(), KV5.getValue());
            System.out.println("Max cache size is: " + cacheCont.cache.getMaxcacheSize());
            System.out.println("Current cache size is: " + cacheCont.cache.getcurrentcacheSize());
            cacheCont.set(KV6.getKey(), KV6.getValue());
            System.out.println("Max cache size is: " + cacheCont.cache.getMaxcacheSize());
            System.out.println("Current cache size is: " + cacheCont.cache.getcurrentcacheSize());

            cacheCont.delete(KV3.getKey());
            System.out.println(KV1.getKey() + " is in cache: " + cacheCont.inCache(KV1.getKey()));
            System.out.println("Max cache size is: " + cacheCont.cache.getMaxcacheSize());
            System.out.println("Current cache size is: " + cacheCont.cache.getcurrentcacheSize());


            //KeyValue temp = cacheCont.get(KV1.getKey());
            cacheCont.cache.printContent();
            cacheCont.cache.clearCache();
            cacheCont.cache.printContent();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}