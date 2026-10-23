package io.github.blk185.xiaomicleanhome;

import java.io.*;
import java.security.*;
import java.util.*;

/** Compare file contents, never filenames. Bounded cache is invalidated by size and modification time. */
final class PhotoFingerprint {
    private static final Map<String,String> cache=new LinkedHashMap<String,String>(128,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,String> entry){return size()>512;}
    };
    static synchronized String of(File file)throws IOException {
        String key=file.getCanonicalPath()+":"+file.length()+":"+file.lastModified();
        String known=cache.get(key);if(known!=null)return known;
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream input=new BufferedInputStream(new FileInputStream(file))){byte[] buffer=new byte[65536];for(int n;(n=input.read(buffer))!=-1;)digest.update(buffer,0,n);}
            StringBuilder result=new StringBuilder();for(byte b:digest.digest())result.append(String.format(Locale.ROOT,"%02x",b&255));
            String value=result.toString();cache.put(key,value);return value;
        }catch(NoSuchAlgorithmException e){throw new IOException(e);}
    }
}
