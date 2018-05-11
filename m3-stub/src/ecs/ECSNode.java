package ecs;

import common.datatypes.MetaData;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Map;
import java.util.TreeMap;

public class ECSNode implements  IECSNode, Serializable{
    private String name;
    private String host;
    private int port;
    private BigInteger startHash;
    private BigInteger endHash;
    private TreeMap<BigInteger, MetaData> metaData;
    private String cacheStrategy;
    private int cacheSize;
    private ECSNodeFlag flag;

    public ECSNode(String name, String host, int port){
        this.name = name;
        this.host = host;
        this.port = port;
        this.startHash = null;
        this.endHash = null;
        metaData = new TreeMap<>();
        flag = ECSNodeFlag.STOP;
    }

    public String getNodeName(){
        return name;
    }

    public String getNodeHost(){
        return host;
    }

    public int getNodePort(){
        return port;
    }

    public String[] getNodeHashRange(){
        String[] hashes = new String[2];
        hashes[0] = startHash.toString();
        hashes[1] = endHash.toString();
        return hashes;
    }

    public void setHashRanges(BigInteger start, BigInteger end){
        this.startHash = start.add(new BigInteger("1"));
        this.endHash = end;
    }

    public void setMetaData(TreeMap<BigInteger, MetaData> set) {
        metaData = set;
    }

    public TreeMap<BigInteger, MetaData> getMetaData(){
        return metaData;
    }

    public void setCacheInfo(int cacheSize, String cacheStrategy){
        this.cacheSize = cacheSize;
        this.cacheStrategy = cacheStrategy;
    }

    public int getCacheSize(){
        return cacheSize;
    }

    public String getCacheStrategy() {
        return cacheStrategy;
    }

    public ECSNodeFlag getFlag(){
        return flag;
    }

    public void setFlag(ECSNodeFlag newFlag){
        flag = newFlag;
    }
}
