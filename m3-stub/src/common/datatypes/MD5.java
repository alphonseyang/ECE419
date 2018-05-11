package common.datatypes;
import java.security.*;
import java.math.*;

public class MD5
{
    public static BigInteger HashInBI(String s) throws NoSuchAlgorithmException {
        MessageDigest m = MessageDigest.getInstance("MD5");
        m.update(s.getBytes(),0,s.length());
        return new BigInteger(1,m.digest());
    }

    public static String HashInStr(String s) throws NoSuchAlgorithmException {
        MessageDigest m = MessageDigest.getInstance("MD5");
        m.update(s.getBytes(),0,s.length());
        String hashStr = new BigInteger(1,m.digest()).toString(16);

        if(hashStr.length() < 32)
        {
            int padding = 32 - hashStr.length();
            String pad = new String(new char[padding]).replace("\0", "0");
            hashStr = pad + hashStr;
        }
        return hashStr;
    }

}
