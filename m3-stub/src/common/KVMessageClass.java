package common;
import common.datatypes.MetaData;
import ecs.IECSNode;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.TreeMap;

public class KVMessageClass implements KVMessage,Serializable{
	
    private StatusType status;
    private String key;
    private String value;
    private TreeMap<BigInteger, MetaData> metaData;
    public CommunicationType type;

    public KVMessageClass(StatusType status, String InpuKey, String InputValue)
    {
        this.status = status;
        this.key = InpuKey;
        this.value = InputValue;
        this.metaData = null;
        this.type=null;

    }
    //In order to identify calls from Server.Temp Change


    public KVMessageClass(StatusType status, String InpuKey, String InputValue,CommunicationType type)
    {
	    this.status = status;
	    this.key = InpuKey;
	    this.value = InputValue;
        this.metaData = null;
        this.type= type;

    }
    public CommunicationType getCommunicationType()
    {
        return this.type;
    }
    //*****END OF TEMP CHANGE

    @Override
    public String getKey(){
        return this.key;
    }

    /**
     * @return the value that is associated with this message,
     * 		null if not value is associated.
     */
    @Override
    public String getValue(){
        return this.value;
    }

    /**
     * @return a status string that is used to identify request types,
     * response types and error types associated to the message.
     */
    @Override
    public StatusType getStatus(){
        return this.status;
    }


    @Override
    public IECSNode getResponsibleServer(){
        return null;
    }

    @Override
    public void setMetaData(TreeMap<BigInteger, MetaData> metaData){
        this.metaData = metaData;
    }

    @Override
    public TreeMap<BigInteger, MetaData> getMetaData(){
        return metaData;
    }

}
