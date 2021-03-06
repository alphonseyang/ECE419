package persistent_storage;
import java.io.IOException;
import  java.nio.ByteBuffer;
import  java.io.RandomAccessFile;
import java.util.Map;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.FileInputStream;
import java.io.File;
import org.apache.log4j.Logger;


public class StorageManager  {

    //private String key;
    //private String value;
    //private ValidityStatus Validity;
    private String DatabaseName;
    private int PortNumber;
    private String LookuptableName;
    private Map<String, LookUpTable> synchronizedMap;
    private static Logger logger = Logger.getRootLogger();

    public enum StatusType {
        GET_ERROR, 		/* requested tuple (i.e. value) not found */
        GET_SUCCESS, 	/* requested tuple (i.e. value) found */
        PUT_SUCCESS, 	/* Put - request successful, tuple inserted */
        PUT_UPDATE, 	/* Put - request successful, i.e. value updated */
        PUT_ERROR, 		/* Put - request not successful */
        DELETE_SUCCESS, /* Delete - request successful */
        DELETE_ERROR 	/* Delete - request not successful */
    }
    /*
    * This method should
    * */
    public StorageManager(int portNumber)
    {
        this.PortNumber=portNumber;
        this.DatabaseName="Server"+portNumber+".txt";
        //Initialize Lookup table
        this.synchronizedMap=null;
        this.LookuptableName="LookupTable"+portNumber+".txt";

    }
    public void initilizateStoreManager()
    {
        Map<String, LookUpTable> map = new HashMap<String, LookUpTable>();
        map=LoadLookupTable();
        this.synchronizedMap= Collections.synchronizedMap(map);
        logger.info("Initialize StorageManager");
    }

	public Map LoadLookupTable(){
        Map<String, LookUpTable> map = null;
        try {
            File file = new File(LookuptableName); //here you make a filehandler - not a filesystem file.

            if(!file.exists()) {
                return new HashMap<String, LookUpTable>();
            }
            FileInputStream fileIn = new FileInputStream(LookuptableName);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            map= (Map<String, LookUpTable>) in.readObject();
            in.close();
            fileIn.close();
            logger.info("Load LookUp Table");
        } catch (IOException i) {
            i.printStackTrace();
            logger.error("Load LookUp Table IOExpection");

            return new HashMap<String, LookUpTable>();
        } catch (ClassNotFoundException c) {
            logger.error("Load LookUp Table ClassNotFoundException");
            c.printStackTrace();
            return new HashMap<String, LookUpTable>();
        }



        return map;
    }
    public Boolean SaveLookupTable(Map<String, LookUpTable> map){
        try {
            FileOutputStream fileOut =
                    new FileOutputStream(LookuptableName,false);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(map);
            out.close();
            fileOut.close();
            logger.info("Serialized data is saved in "+LookuptableName);
            //System.out.println("Serialized data is saved in lookuptable.txt");
        } catch (IOException i) {
            i.printStackTrace();
            logger.error("Load LookUp Table IOException");

        }
        return false;
    }
    public boolean DeleteKV(String Key){
        //empty string =null
        boolean result= synchronizedMap.containsKey(Key);
        if(result) //Key Exist
        {
            synchronizedMap.remove(Key);
            SaveLookupTable(synchronizedMap);
            logger.info("Delete Key: "+Key+" from FileSystem");
        }
        else //Key does not exist
        {
            logger.info("Could not find "+Key+" in File System");
            return result;
        }
        return false;
    }

    public StatusType PutKV(String Key, String Value){

        LookUpTable a= (LookUpTable)synchronizedMap.get(Key);
       // StorageManager test= new StorageManager(key,value,null);

        if(a!=null){
            //System.out.println("update command");
            //Step 1 :Change the old location to invalid
            ModifyValidByte(a.StartPoint,a.Length);
            //Step 2 : Save update KV to disk
            byte[] msgbytes=ParseMessage(Key,Value,true);
            long location=saveMsgToDisk(msgbytes);
            //Step 3 :Update the synchronizedMap
            //System.out.println("Update "+a.Length+"hello"+a.StartPoint);
            a.Length=msgbytes.length;
            a.StartPoint=location;
            synchronizedMap.put(Key,a);
            logger.info("Update Key: "+Key+"Value: "+Value+" in FileSystem");

        }
        else
        {
            byte[] Msg= ParseMessage(Key,Value,true);
            long location=saveMsgToDisk(Msg);
            long size=Msg.length;
            synchronizedMap.put(Key, new LookUpTable(location,size));
            logger.info("Create Key: "+Key+" Value: "+Value+" in FileSystem");

        }
        SaveLookupTable(synchronizedMap);

        return  StatusType.PUT_SUCCESS;
    }
    public StatusType UpdateKV(String Key, String Value){
        return  StatusType.PUT_SUCCESS;
    }

    public  String GetKV(String Key)throws Exception{
        LookUpTable a= (LookUpTable)synchronizedMap.get(Key);
        if (a!=null){//Find the KV pair in the Mapping Table
           byte[] result= findMsgFromDisk(a.StartPoint,a.Length);
            String s=ParseByteArray(result);
            logger.info("Key: "+Key+ "exist "+" in FileSystem");
            return s;
        }
        else
        {
            logger.info("Key: "+Key+ " does not exist "+" in FileSystem");
            return null;

        }

    }
    public String ParseByteArray(byte[]Bytes){
        //Check Correct format of the Bytes Array
        int counter=0;
        int i;
        for(i=0;i<4;i++) if (Bytes[counter++]!=0xD) return "";//3
        if(Bytes[++counter]!=0) return "";//4
        byte[]KeyLength=new byte[4];
        for(i=0;i<4;i++)
            KeyLength[i]=Bytes[counter++];//8
        int keylength = ByteBuffer.wrap(KeyLength).getInt();
        counter+=keylength;
        byte[]ValueLength=new byte[4];
        for(i=0;i<4;i++)
            ValueLength[i]=Bytes[counter++];//8
        int valuelength = ByteBuffer.wrap(ValueLength).getInt();

        byte[]Value=new byte[valuelength];
        for (i=0;i<valuelength;i++){
            Value[i]=Bytes[counter+i];
        }
        logger.info("Convert Byte Array to "+new String(Value).trim()+" in FileSystem");

        return new String(Value).trim();



    }

    public  byte[] ParseMessage(String key,String Value,boolean Status)
    {
        String message="\r\r\r\r"; //Initial Identifier
        byte[] MessageBytes=message.getBytes();
        byte []Validity = new byte[(byte)(Status?1:0)];
        //Contain Identifer and Validity
        byte[] tmp=new byte[MessageBytes.length+1];
        System.arraycopy(MessageBytes, 0, tmp, 0, MessageBytes.length);
        System.arraycopy(Validity, 0, tmp, MessageBytes.length, 1);


        //KeyLength Bytes Array

        byte[] KeyByteArray=key.getBytes();
        int KeyLength=KeyByteArray.length;
        byte[] KeyLengthBytes = ByteBuffer.allocate(4).putInt(KeyLength).array();
       // int pomAsInt = ByteBuffer.wrap(bytes).getInt();

        byte []tmp1=new byte[KeyLengthBytes.length+KeyByteArray.length];
        System.arraycopy(KeyLengthBytes, 0, tmp1, 0, KeyLengthBytes.length);
        System.arraycopy(KeyByteArray, 0, tmp1, KeyLengthBytes.length, KeyByteArray.length);

        //ValueByteArray
        byte[] ValueByteArray=Value.getBytes();
        int ValueLength=ValueByteArray.length;
        byte[] ValueLengthBytes = ByteBuffer.allocate(4).putInt(ValueLength).array();
        // Cast back methods int pomAsInt = ByteBuffer.wrap(bytes).getInt();
        byte []tmp2=new byte[ValueByteArray.length+ValueLengthBytes.length];
        System.arraycopy(ValueLengthBytes, 0, tmp2, 0, ValueLengthBytes.length);
        System.arraycopy(ValueByteArray, 0, tmp2, ValueLengthBytes.length, ValueByteArray.length);

        byte[]finalArray= new byte[tmp.length+tmp1.length+tmp2.length];
        System.arraycopy(tmp, 0, finalArray, 0, tmp.length);
        System.arraycopy(tmp1, 0, finalArray, tmp.length, tmp1.length);
        System.arraycopy(tmp2, 0, finalArray, tmp1.length+tmp.length, tmp2.length);



        logger.info("Convert Key: "+key+" to Byte Array in FileSystem");

        return finalArray;
    }

    /*
    * Description: This function will insert the msg into the end of the file
    * Input: Parsed KVmessage in byte array format
    * Output: Return the Start Location of the this MSG
    *         Result should greater than 0 and -1 means error
    * */
    public synchronized long saveMsgToDisk(byte[] ByteFormatMsg){
        long location;
        try {
            RandomAccessFile raf = new RandomAccessFile(this.DatabaseName, "rw");
            location=raf.length();
            raf.seek(location);
            raf.write(ByteFormatMsg);
            raf.close();
            logger.info("Write Byte Array to disk");

        }catch (IOException e){
            logger.error("Write to disk failed");
            return  -1;
        }

        return location;
    }

    /*
    * Return bytes[] array
    * Return null if not found
    * */
    public synchronized byte[] findMsgFromDisk(long location,long length){
        byte[] bytes=null;
        try{
            RandomAccessFile raf = new RandomAccessFile(this.DatabaseName, "r");
            raf.seek(location);
            bytes=new byte[(int)length];
            raf.read(bytes);
            raf.close();
            logger.info("Find Byte Array From disk");

        }catch (IOException e){
            logger.error("Read disk failed");

        }
        return bytes;

    }
    /*
    * Description: This function will modify the valid Byte in the Current Entry.
    * We may need to clean up the garbage entries at some points.
    * */
    public synchronized boolean ModifyValidByte(long Start,long length){
        try {
            RandomAccessFile raf = new RandomAccessFile(this.DatabaseName, "rw");
            raf.seek(Start);
            byte[] bytes=new byte[(int)length];
            raf.read(bytes);
            //for (int i=0;i<bytes.length;i++)
            //{
             //   System.out.println(bytes[i]);
           // }
            bytes[4]=1;
            raf.seek(Start);
            raf.write(bytes);
            raf.close();
            logger.info("Modify Valid Byte at Location: "+Start);


        }catch (IOException e){
            logger.error("Modify Valid Byte Failed");
            return false;
        }

        return  true;
    }
    public void clearStorage(){
        if(synchronizedMap!=null) {
            synchronizedMap.clear();
            //SaveLookupTable(synchronizedMap);
            logger.info("Clear synchronizedMap");
        }
        File f = null;
        f=new File(this.DatabaseName);
        try {
            boolean result= f.delete();
            logger.info("File "+this.DatabaseName+" deleted: "+result);

        } catch (Exception x) {
            logger.error("File" + this.DatabaseName + "does not exist");
        }
        f=new File(this.LookuptableName);
        try {
            boolean result= f.delete();
            logger.info("File "+this.LoadLookupTable()+" deleted: "+result);

        } catch (Exception x) {
            logger.error("File" + this.LookuptableName + "does not exist");
        }


    }
    public void TestFunction(){

        try {
            RandomAccessFile raf = new RandomAccessFile(this.DatabaseName, "r");
            //raf.seek(raf.length());
            byte[] bytes = new byte[(int)raf.length()];
            raf.read(bytes);
            System.out.println("Read disk ");

            for (int i=0;i<bytes.length;i++)
            {
                System.out.println(bytes[i]);
            }
            raf.close();
        }catch (IOException e){
            System.out.println("Read disk failed");
        }

    }
}
