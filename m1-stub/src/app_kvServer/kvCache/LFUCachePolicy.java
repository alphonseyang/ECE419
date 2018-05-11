package app_kvServer.kvCache;

import app_kvServer.KeyValue;
import app_kvServer.kvCache.cachePolicies;

import java.util.Collections;
import java.util.*;

public class LFUCachePolicy extends cachePolicies {
    private Map<String, String> cacheList;
    private Map<String, Integer> frequencyList;

    public LFUCachePolicy(int cacheSize) {
        super(cacheSize);

        this.cacheList = (Map<String, String>)
                Collections.synchronizedMap(
                        new LinkedHashMap<String, String>(cacheSize, 0.75f, true));
        this.frequencyList = (Map<String, Integer>)
                Collections.synchronizedMap(
                        new TreeMap<String, Integer>());
    }

    public static <K, V extends Comparable<V>> Map<K, V>
    sortByValues(final Map<K, V> map) {
        Comparator<K> valueComparator =
                new Comparator<K>() {
                    public int compare(K k1, K k2) {
                        int compare =
                                map.get(k1).compareTo(map.get(k2));
                        if (compare == 0)
                            return 1;
                        else
                            return compare;
                    }
                };

        Map<K, V> sortedByValues =
                new TreeMap<K, V>(valueComparator);
        sortedByValues.putAll(map);
        return sortedByValues;
    }

    private void frequencyUpdate(String key) {
        synchronized (frequencyList) {
            Integer newFrequency = frequencyList.get(key) + 1;
            frequencyList.put(key, newFrequency);
        }
    }

    @Override
    public KeyValue readCache(String key) {
        synchronized (cacheList) {
            synchronized (frequencyList) {
                if (cacheList.containsKey(key)) {
                    KeyValue keyValue = new KeyValue(key, cacheList.get(key));
                    frequencyUpdate(key);
                    return keyValue;
                }
                else {
                    return null;
                }
            }
        }
    }

    @Override
    public void writeCache(KeyValue KV) {
        synchronized (cacheList) {
            synchronized (frequencyList) {
                if (maxcacheSize == currentcacheSize) {
                    evict();// Call Make space function
                }
                cacheList.put(KV.getKey(), KV.getValue());
                frequencyList.put(KV.getKey(), 1);
                currentcacheSize += 1;
            }
        }
    }

    @Override
    public boolean inCache(String key) {
        synchronized (frequencyList) {
            Set keyset = frequencyList.keySet();
            return keyset.contains(key);
        }
    }

    @Override
    public void deleteCache(String key) {
        synchronized (cacheList) {
            synchronized (frequencyList) {
                cacheList.remove(key);
                frequencyList.remove(key);

                currentcacheSize -= 1;
            }
        }
    }

    private void evict() {
        Map sortedMap = sortByValues(frequencyList);
        Map.Entry temp = (Map.Entry) sortedMap.entrySet().iterator().next();
        frequencyList.remove(temp.getKey());
        cacheList.remove(temp.getKey());

        currentcacheSize -= 1;
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
        frequencyList.clear();
        currentcacheSize = 0;
    }
}