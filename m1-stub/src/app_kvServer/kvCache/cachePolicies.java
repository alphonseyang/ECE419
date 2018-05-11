package app_kvServer.kvCache;

import app_kvServer.KeyValue;

public abstract class cachePolicies {
	protected int maxcacheSize;
	protected int currentcacheSize = 0;

	public cachePolicies (int cacheSize) {
        this.maxcacheSize = cacheSize;
        this.currentcacheSize = 0;
	}

	protected abstract KeyValue readCache(String key);
	protected abstract void writeCache(KeyValue KV);
	protected abstract void deleteCache(String key);
	protected abstract boolean inCache(String key);
	protected abstract void clearCache();

	public abstract int getMaxcacheSize();
	public abstract int getcurrentcacheSize();
	public abstract void printContent();
}