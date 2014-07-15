package com.ess.jloader.packer.tests;

import com.ess.jloader.packer.Config;
import com.ess.jloader.packer.JarMetaData;
import com.ess.jloader.packer.JarPacker;
import com.google.common.util.concurrent.AtomicLongMap;
import org.junit.Test;
import org.objectweb.asm.ClassReader;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/**
 * @author Sergey Evdokimov
 */
public class PackFreeMarker {

    private void printStats(JarPacker packer) throws IOException {
        Collection<ClassReader> classes = packer.getClassMap().values();

        System.out.println("Strings in Metadata: " + new JarMetaData(packer.getClassMap()).getStringsMap().size());

        AtomicLongMap<String> map = AtomicLongMap.create();

        long totalSize = 0;
        long utfSize = 0;

        ByteArrayOutputStream utfArray = new ByteArrayOutputStream();
        GZIPOutputStream utfStream = new GZIPOutputStream(utfArray);

        ByteArrayOutputStream codeArray = new ByteArrayOutputStream();
        GZIPOutputStream codeStream = new GZIPOutputStream(codeArray);


        for (ClassReader aClass : classes) {
            totalSize += aClass.b.length;

            byte[] copyB = aClass.b.clone();

            for (int i = 0; i < aClass.getItemCount() - 1; i++) {
                int pos = aClass.getItem(i + 1);

                if (pos > 0 && aClass.b[pos - 1] == 1) {
                    int strSize = new DataInputStream(new ByteArrayInputStream(aClass.b, pos, aClass.b.length)).readShort() & 0xFFFF;
                    utfSize += strSize + 3;

                    String s = new DataInputStream(new ByteArrayInputStream(aClass.b, pos, aClass.b.length)).readUTF();
                    map.incrementAndGet(s);

                    Arrays.fill(copyB, pos - 1, pos + strSize + 2, (byte) 0);
                }
            }

            codeStream.write(copyB);
        }

        List<String> lll = new ArrayList<String>(map.asMap().keySet());
        Collections.sort(lll);

        int utfDistinctSize = 0;

        for (String s : lll) {
            utfStream.write(s.getBytes());
            utfDistinctSize += s.getBytes().length + 2;
        }

        codeStream.finish();
        utfStream.finish();

        System.out.printf("Class count: %d (%d bytes)\n", classes.size(), totalSize);
        System.out.printf("UTF count: %d (%d bytes, %d%%), distinctSize: %d\n", map.size(), utfSize, utfSize * 100 / totalSize, utfDistinctSize);

        System.out.printf("Code packed: %d , Utf packed: %d, Sum: %d\n", codeArray.size(), utfArray.size(), codeArray.size() + utfArray.size());

        AtomicLongMap<Long> ccc = AtomicLongMap.create();
        for (Long aLong : map.asMap().values()) {
            ccc.incrementAndGet(aLong);
        }
        System.out.println();
        System.out.println(TestUtils.sort(ccc.asMap()));
        System.out.println();
        Map<String, Long> longMap = TestUtils.sort(map.asMap());
        TestUtils.printFirst(longMap, 200);
    }

    @Test
    public void packFreeMarker() throws IOException {
        File freeMarkerJar = TestUtils.getJarByMarker("freemarker/core/Assignment.class");

        File packedFreeMarker = TestUtils.createTmpPAckFile("packedFreeMarker");
        JarPacker.pack(freeMarkerJar, packedFreeMarker);

        System.out.printf("Src: %d, Result: %d,  (%d%%)", freeMarkerJar.length(), packedFreeMarker.length(), packedFreeMarker.length() * 100 / freeMarkerJar.length());
    }

    @Test
    public void statFreeMarker() throws IOException {
        File freeMarkerJar = TestUtils.getJarByMarker("freemarker/core/Assignment.class");

        JarPacker packer = new JarPacker(new Config());
        packer.addJar(freeMarkerJar);

        printStats(packer);
    }

    @Test
    public void statIdea() throws IOException {
        File freeMarkerJar = new File("/home/user/EAP/idea-IU-138.777/lib/idea.jar");

        JarPacker packer = new JarPacker(new Config());
        packer.addJar(freeMarkerJar);

        printStats(packer);
    }

//    @Test
//    public void packFreeMarker() throws IOException {
//        File freeMarkerJar = TestUtils.getJarByMarker("freemarker/core/Assignment.class");
//
//        File packedFreeMarker = TestUtils.createTmpPAckFile("packedFreeMarker");
//
//        JarPacker packer = new JarPacker(new Config());
//        packer.addJar(freeMarkerJar);
//
//        printStats(packer.getClassMap().values());
//
//        System.out.printf("Src: %d, Result: %d,  (%d%%)", freeMarkerJar.length(), packedFreeMarker.length(), packedFreeMarker.length() * 100 / freeMarkerJar.length());
//    }

}
