package common.messages;
import java.io.Serializable;

public class KVMessageClass implements KVMessage,Serializable{
	
    private StatusType status;
    private String key;
    private String value;

    public KVMessageClass(StatusType status, String InpuKey, String InputValue)
    {
	    this.status = status;
	    this.key = InpuKey;
	    this.value = InputValue;
    }

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

}
