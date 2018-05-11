package common;

import common.datatypes.MetaData;
import ecs.IECSNode;

import java.math.BigInteger;
import java.util.TreeMap;

public interface KVMessage {

	public enum StatusType {
		GET, 			/* Get - request */
		GET_ERROR, 		/* requested tuple (i.e. value) not found */
		GET_SUCCESS, 	/* requested tuple (i.e. value) found */
		PUT, 			/* Put - request */
		PUT_SUCCESS, 	/* Put - request successful, tuple inserted */
		PUT_UPDATE, 	/* Put - request successful, i.e. value updated */
		PUT_ERROR, 		/* Put - request not successful */
		DELETE_SUCCESS, /* Delete - request successful */
		DELETE_ERROR, 	/* Delete - request successful */

		SERVER_STOPPED,         /* Server is stopped, no requests are processed */
		SERVER_WRITE_LOCK,      /* Server locked for out, only get possible */
		SERVER_NOT_RESPONSIBLE  /* Request not successful, server not responsible for key */
	}

	public enum CommunicationType {
		ECS_MoveData,
		ECS_ReceiveData,
		ECS_ReceiveDataFinished,
		ECS_Default
	};


	/**
	 * @return the key that is associated with this message,
	 * 		null if not key is associated.
	 */
	public String getKey();

	/**
	 * @return the value that is associated with this message,
	 * 		null if not value is associated.
	 */
	public String getValue();

	/**
	 * @return a status string that is used to identify request types,
	 * response types and error types associated to the message.
	 */
	public StatusType getStatus();

	/**
     * @return  the responsible server node
     */
    public IECSNode getResponsibleServer();

	public void setMetaData(TreeMap<BigInteger, MetaData> metaData);

    public TreeMap<BigInteger, MetaData> getMetaData();

    public CommunicationType getCommunicationType();

}
