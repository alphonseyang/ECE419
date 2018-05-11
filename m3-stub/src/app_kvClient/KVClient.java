package app_kvClient;

import client.KVCommInterface;

import java.io.*;
import java.net.UnknownHostException;

import logger.LogSetup;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import client.KVStore;
import common.KVMessage;
import org.junit.Test;
////TEST PURPOSE!!!!
import java.lang.Exception;
import java.util.ArrayList;
import java.util.HashMap;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.TreeMap;


public class KVClient implements IKVClient {
  private static Logger logger = Logger.getRootLogger();
  private boolean stop = false; // Flag to indicate stop or not
  private BufferedReader stdin;
  private static final String PROMPT = "M3-Client> ";
  private String serverAddress;
  private int serverPort;

  private KVStore store = null;
  private TreeMap<String,KVStore>Watch_list=new TreeMap<>();
  public static void main(String[] args) {

    try {
      new LogSetup("logs/client.log", Level.INFO);
      KVClient client = new KVClient();
      client.run();
    } catch (IOException e) {
      System.out.println("Unable to initialize logger!");
      e.printStackTrace();
      System.exit(1);
    }

  }

  @Override
  public void newConnection(String hostname, int port) throws UnknownHostException, Exception {
    store = new KVStore(hostname, port,true);
    store.connect();
  }

  @Override
  public KVCommInterface getStore() {
    return store;
  }

  public void handleCommand(String cmdLine) {
    String[] tokens = cmdLine.split("\\s+");
    if (tokens[0].equals("quit")) {
      stop = true;
      if (store != null) {
        store.disconnect();
        store = null;
      }
      logger.info(PROMPT + "Application exit!");

    } else if (tokens[0].equals("connect")) {
      if (tokens.length == 3) {
        try {
          serverAddress = tokens[1];
          serverPort = Integer.parseInt(tokens[2]);
          newConnection(serverAddress, serverPort);
          logger.info("KV Client Successfully connected to server address: " + serverAddress + " port number: " + serverPort);
          // store.receiveMessage();
        } catch (NumberFormatException nfe) {
          printError("No valid address. Port must be a number!");
          // logger.info("Unable to parse argument <port>", nfe);
        } catch (UnknownHostException e) {
          printError("Unknown Host!");
          // logger.info("Unknown Host!", e);
        } catch (Exception e) {
          printError("Could not establish connection!");
          // logger.warn("Could not establish connection!", e);
        }
      } else {
        printError("Invalid number of parameters!");
      }

    } else if (tokens[0].equals("put")) {
      if (tokens.length >= 2) {
        if (store != null && store.isConnected()) {
          String key = tokens[1];
          if (key.length() <= 20 && key.length() > 0) {
            String value = convertToValue(tokens);

            if (value == null || (value != null && value.length() <= 122880)) {
              try {
                KVMessage temp = store.put(key, value);
                logger.info("Status: " + temp.getStatus() + " Key: " + temp.getKey() + " Value: " + temp.getValue());
              } catch (Exception e) {
                logger.error("Put message error " + e);
              }
            }
          } else {
            printError("Key length should be between 1 and 20!");
          }

        } else {
          printError("Not connected");
        }
      } else {
        printError("Invalid number of parameters!");
      }
    } else if (tokens[0].equals("get")) {
      if (tokens.length == 2) {
        if (store != null && store.isConnected()) {
          String key = tokens[1];
          try {
            KVMessage temp = store.get(key);
            logger.info("Status: " + temp.getStatus() + " Key: " + temp.getKey() + " Value: " + temp.getValue());
          } catch (Exception e) {
            logger.error("Get message error " + e);
          }
        } else {
          printError("Not connected");
        }
      } else {
        printError("Invalid number of parameters!");
      }
    } else if (tokens[0].equals("disconnect")) {
      if (tokens.length == 1) {
        store.disconnect();
        logger.info("KV Client successfully disconnected");
      } else {
        printError("Invalid number of parameters!");
      }
    } else if (tokens[0].equals("help")) {
      if (tokens.length == 1) {
        printHelp();
      } else {
        printError("Invalid number of parameters!");
      }
    } else if (tokens[0].equals("transfer")) {
      try {

        RandomAccessFile raf = new RandomAccessFile("Server20000.txt", "r");

        byte[] b = new byte[(int) raf.length()];
        raf.readFully(b);
        System.out.println("size of file is " + b.length);
        String str = new String(b, "UTF-8"); // for UTF-8 encoding
        System.out.println("size of file is " + str.length());

        //store.moveData(str);
        raf.close();

      } catch (IOException e) {
        ;
      } catch (Exception e) {
        ;
      }
    } else if (tokens[0].equals("performance")) {
      int port=50001;
      if (tokens.length == 2) {port=Integer.parseInt(tokens[1]);}
      String[] files={"arnold-j","allen-p","arora-h","badeer-r","bailey-s","germany-c"};
      HashMap<String, String> Database = traverse_file();
      for (int i = 0; i < 1; i++) {
        //127.0.0.1 50007
        try {
          serverAddress = "127.0.0.1";
          serverPort = port;
          newConnection(serverAddress, serverPort);
          logger.info("KV Client Successfully connected to server address: " + serverAddress + " port number: " + serverPort);
          // store.receiveMessage();
        } catch (NumberFormatException nfe) {
          printError("No valid address. Port must be a number!");
          // logger.info("Unable to parse argument <port>", nfe);
        } catch (UnknownHostException e) {
          printError("Unknown Host!");
          // logger.info("Unknown Host!", e);
        } catch (Exception e) {
          printError("Could not establish connection!");
          // logger.warn("Could not establish connection!", e);
        }
        final long startTime = System.nanoTime();
        long countbyte=0;

        for (String key : Database.keySet()) {

          try {
            KVMessage temp = store.put(key, Database.get(key));
            countbyte+=Database.get(key).length();
            //logger.info("Status: "+temp.getStatus() + " Key: "+temp.getKey()+" Value: "+temp.getValue());
          } catch (Exception e) {
            logger.error("Put message error "+e);
          }
        }

        final long duration = System.nanoTime() - startTime;
        long result = duration;
        System.out.println("Total Write Time per request "+String.valueOf(result)+" ns");
        int size=Database.size();
        System.out.println("size: "+size);
        System.out.println("Byte size "+countbyte+"B");
        float averageWriting=(float)((duration/size)/Math.pow(10,6));
        System.out.println("Average Writing time per request "+String.format("%.2f",averageWriting)+" ms/request");
        float writeThroughput=(float)(((countbyte*(Math.pow(10,6)))/result));
        System.out.println("Write throughtput is "+String.format("%.2f", writeThroughput)+"KB per Second");

        final long newstartTime = System.nanoTime();

        for (String key : Database.keySet()) {

          try {
            KVMessage temp = store.get(key);
            //logger.info("Status: "+temp.getStatus() + " Key: "+temp.getKey()+" Value: "+temp.getValue());
          } catch (Exception e) {
            logger.error("Put message error "+e);
          }
        }

        final long newduration = System.nanoTime() - newstartTime;
        long newresult = newduration;
        System.out.println("Total Read Time "+String.valueOf(newresult)+"ns");
        long newsize=Database.size();
        float averageread=(float)((newduration/size)/Math.pow(10,6));
        System.out.println("Average Read Time per request"+String.format("%.2f",averageread)+" ms/request");
        //System.out.println("Average Read ThroughPut"+String.valueOf(1/(newresult/newsize)));
        float readThroughput=(float)(((countbyte*(Math.pow(10,6)))/newresult));

        System.out.println("Read throughtput is "+String.format("%.2f",readThroughput)+"KB per Second");

        return;
      }
    }
    else if(tokens[0].equals("watch"))
    {
      if (tokens.length == 2) {

        String key= tokens[1];
        try{
          KVStore watch_store;
          watch_store = new KVStore(this.serverAddress, this.serverPort,key);
          Watch_list.put(key,watch_store);
          watch_store.connect();
        }catch (Exception e){
          System.out.println("Watch event failed for key:"+key);
        }

      }

    }
    else if(tokens[0].equals("unwatch"))
    {
      if (tokens.length == 2) {

        String key = tokens[1];
        Watch_list.get(key).disconnect();
        logger.info("Unwatch key: "+key);

      }
    }
    else if (tokens[0].equals("debug")){
      logger.setLevel(Level.DEBUG);
      logger.info("Logger level set to debug");
    }
    else if (tokens[0].equals("info")){
      logger.setLevel(Level.INFO);
      logger.info("Logger level set to info");
    }
    else {
      printError("Wrong command!");
    }



  }

  private void printError(String error) {
    logger.error(PROMPT + "Error! " + error);
  }

  private String convertToValue(String[] tokens){
    // in the case that there's no value, so it is a delete command
    if (tokens.length == 2) return null;

    StringBuilder sb = new StringBuilder();
    for (int i = 2; i < tokens.length-1; i++){
      sb.append(tokens[i]);
    }
    sb.append(tokens[tokens.length-1]);
    return sb.toString();
  }

  public void run() {
    while (!stop) {
      stdin = new BufferedReader(new InputStreamReader(System.in));
      System.out.print(PROMPT);

      try {
        String cmdLine = stdin.readLine();
        this.handleCommand(cmdLine);
      } catch (IOException e) {
        stop = true;

        printError("CLI does not respond - Appilication terminated ");
      }

    }
  }

  private void printHelp(){
    logger.info("connect <ServerAddress> <PortNumber> ; " +
            "put <key> <value>; " +
            "get <key>; disconnect; " +
            "help;" +
            "debug" +
            "info");
  }
  private HashMap<String,String> traverse_file() {

    Path currentRelativePath = Paths.get("");
    String s = currentRelativePath.toAbsolutePath().toString();
    System.out.println("Current relative path is: " + s);
    String path="/Users/zeyang/Documents/GitHub/Distribution-System/m3-stub/Performance/";
    File folder1 = new File(path);
    File[] listOfFiless = folder1.listFiles();
    ArrayList<String> folderNames = new ArrayList<String>();

    for(int i=0;i<listOfFiless.length;i++)
    {
      //System.out.println(listOfFiless[i].getName());
      folderNames.add(path+listOfFiless[i].getName());
    }
    HashMap<String, String> TestDataBase = new HashMap<String, String>();



    for (int a = 0; a < folderNames.size(); a++) {

      String path1 = folderNames.get(a);
      //System.out.println(path1);
      File folder = new File(path1);
      File[] listOfFiles = folder.listFiles();
      ArrayList<String> allDirectories = new ArrayList<String>();
      for (int i = 0; listOfFiles!=null&&i < listOfFiles.length; i++) {
        if (listOfFiles[i].isFile()) {
        } else if (listOfFiles[i].isDirectory()) {
          allDirectories.add(path1 + "//" + listOfFiles[i].getName());
        }
      }

      for (String test : allDirectories) {
        File Inner_folder = new File(test);
        // System.out.println("Folder Name"+Inner_folder.getName());
        File[] allfiles = Inner_folder.listFiles();
        for (File file : allfiles) {
          String file_path =  file.getName();

          if (file.isFile() && file_path.length() <= 20) {
            try {
              String Value = "";
              BufferedReader br = new BufferedReader(new FileReader(file));
              String st;
              try {
                while ((st = br.readLine()) != null)
                  Value += st;
              } catch (IOException e) {

              }
              TestDataBase.put(file_path, Value);
            } catch (FileNotFoundException e) {
              logger.error(e + file_path);
            }


          }
        }

      }


    }
    System.out.println(TestDataBase.size());
    return TestDataBase;
  }

}