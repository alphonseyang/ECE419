package app_kvECS;

import java.util.Map;
import java.util.Collection;

import ecs.IECSNode;

public interface IECSClient {
    /**
     * Starts the storage service by calling start() on all KVServer instances that participate in the service.\
     * @throws Exception    some meaningfull exception on failure
     * @return  true on success, false on failure
     */
    public boolean start() throws Exception;

    /**
     * Stops the service; all participating KVServers are stopped for processing client requests but the processes remain running.
     * @throws Exception    some meaningfull exception on failure
     * @return  true on success, false on failure
     */
    public boolean stop() throws Exception;

    /**
     * Stops all server instances and exits the remote processes.
     * @throws Exception    some meaningfull exception on failure
     * @return  true on success, false on failure
     */
    public boolean shutdown() throws Exception;

    /**
     * Create a new KVServer with the specified cache size and replacement strategy and add it to the storage service at an arbitrary position.
     * @return  name of new server
     */
    public IECSNode addNode(String cacheStrategy, int cacheSize);

    /**
     * Randomly choose <numberOfNodes> servers from the available machines and start the KVServer by issuing an SSH call to the respective machine.
     * This call launches the storage server with the specified cache size and replacement strategy. For simplicity, locate the KVServer.jar in the
     * same directory as the ECS. All storage servers are initialized with the metadata and any persisted data, and remain in state stopped.
     * NOTE: Must call setupNodes before the SSH calls to start the servers and must call awaitNodes before returning
     * @return  set of strings containing the names of the nodes
     */
    public Collection<IECSNode> addNodes(int count, String cacheStrategy, int cacheSize);

    /**
     * Sets up `count` servers with the ECS (in this case Zookeeper)
     * @return  array of strings, containing unique names of servers
     */
    public Collection<IECSNode> setupNodes(int count, String cacheStrategy, int cacheSize);

    /**
     * Wait for all nodes to report status or until timeout expires
     * @param count     number of nodes to wait for
     * @param timeout   the timeout in milliseconds
     * @return  true if all nodes reported successfully, false otherwise
     */
    public boolean awaitNodes(int count, int timeout) throws Exception;

    /**
     * Removes nodes with names matching the nodeNames array
     * @param nodeNames names of nodes to remove
     * @return  true on success, false otherwise
     */
    public boolean removeNodes(Collection<String> nodeNames);

    /**
     * Get a map of all nodes
     */
    public Map<String, IECSNode> getNodes();

    /**
     * Get the specific node responsible for the given key
     */
    public IECSNode getNodeByKey(String Key);
}
