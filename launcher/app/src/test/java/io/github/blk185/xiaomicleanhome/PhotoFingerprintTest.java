package io.github.blk185.xiaomicleanhome;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class PhotoFingerprintTest {
    @Test public void renamedCopiesMatchAndDifferentContentsRemainDistinct()throws Exception{
        Path folder=Files.createTempDirectory("photo-fingerprint-");
        try{
            byte[] data=new byte[190000];new Random(93).nextBytes(data);
            File first=folder.resolve("first.jpg").toFile(),copy=folder.resolve("renamed.png").toFile(),other=folder.resolve("other.jpg").toFile();
            Files.write(first.toPath(),data);Files.write(copy.toPath(),data);data[100000]^=1;Files.write(other.toPath(),data);
            assertEquals(PhotoFingerprint.of(first),PhotoFingerprint.of(copy));assertNotEquals(PhotoFingerprint.of(first),PhotoFingerprint.of(other));
            Files.write(copy.toPath(),new byte[]{1,2,3});assertNotEquals(PhotoFingerprint.of(first),PhotoFingerprint.of(copy));
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(folder)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});}}
    }
}
