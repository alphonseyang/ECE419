package ecs;

import common.datatypes.MetaData;

import java.math.BigInteger;
import java.util.TreeMap;

public interface IECSNode {
    public enum ECSNodeFlag{
        STOP,
        START,
        STATE_CHANGE,
        KV_TRANSFER,
        SHUT_DOWN,
        UPDATE,
        TRANSFER_FINISH,
        ERROR
    }

    /**
     * @return  the name of the node (ie "Server 8.8.8.8")
     */
    public String getNodeName();

    /**
     * @return  the hostname of the node (ie "8.8.8.8")
     */
    public String getNodeHost();

    /**
     * @return  the port number of the node (ie 8080)
     */
    public int getNodePort();

    /**
     * @return  array of two strings representing the low and high range of the hashes that the given node is responsible for
     */
    public String[] getNodeHashRange();

    public void setHashRanges(BigInteger startHash, BigInteger endHash);

    public void setMetaData(TreeMap<BigInteger, MetaData> set);

    public TreeMap<BigInteger, MetaData> getMetaData();

    public void setCacheInfo(int cacheSize, String cacheStrategy);

    public int getCacheSize();

    public String getCacheStrategy();

    public ECSNode.ECSNodeFlag getFlag();

    public void setFlag(ECSNode.ECSNodeFlag newFlag);
}
