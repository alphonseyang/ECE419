package persistent_storage;


import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LookUpTable implements Serializable {

    public long StartPoint;
    public long Length;

    public  LookUpTable(long start,long length){

        StartPoint=start;
        Length=length;
    }


}
