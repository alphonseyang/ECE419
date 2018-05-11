package app_kvServer.kvCache;

import app_kvServer.KeyValue;
import app_kvServer.kvCache.cachePolicies;

import java.util.*;

public class LRUCachePolicy extends cachePolicies {

    private Map<String, String> cacheList;

    public LRUCachePolicy (int cacheSize) {
        super(cacheSize);
        // LinkedHashMap construction
        // LinkedHashMap(int initialCapacity, float loadFactor, boolean accessOrder)
        // Set accessOrder to true to sort the key in access order
        this.cacheList = (Map<String, String>)
                Collections.synchronizedMap(
                        new LinkedHashMap<String, String>(cacheSize, 0.75f, true));
    }

    @Override
    public KeyValue readCache(String key) {
        synchronized (cacheList) {
            if (cacheList.containsKey(key)) {
                KeyValue KV = new KeyValue(key, cacheList.get(key));
                return KV;
            }
            else {
                return null;
            }
        }
    }

    @Override
    public void writeCache(KeyValue KV) {
        synchronized (cacheList) {
            if (currentcacheSize == maxcacheSize) {
                evict();
            }

                cacheList.put(KV.getKey(), KV.getValue());
                currentcacheSize += 1;

        }
    }

    @Override
    public boolean inCache(String key) {
        Set keyset = cacheList.keySet();
        return keyset.contains(key);
    }

    @Override
    public void deleteCache(String key) {
        synchronized (cacheList) {
            cacheList.remove(key);

            currentcacheSize -= 1;
        }
    }

    private void evict() {
        // Determine if the entry need to be write to disk
        /*
        String key = cachelist.keySet().iterator().next();
        KeyValue KV = new KeyValue(key, cachelist.get(key));
        cachelist.remove(key);
        */
        synchronized (cacheList) {
            String key = cacheList.keySet().iterator().next();
            cacheList.remove(key);

            currentcacheSize -= 1;
        }
    }

    @Override
    public int getMaxcacheSize() {
        return maxcacheSize;
    }

    @Override
    public int getcurrentcacheSize() {
        return currentcacheSize;
    }

    @Override
    public void printContent() {
        Set set = cacheList.entrySet();

        Iterator i = set.iterator();

        while (i.hasNext()) {
            Map.Entry me = (Map.Entry) i.next();
            System.out.println(me.getKey() + ": ");
            System.out.println(me.getValue());
        }
        System.out.println();
    }

    protected void clearCache() {
        cacheList.clear();
        currentcacheSize = 0;
    }
}