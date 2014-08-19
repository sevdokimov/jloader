package com.ess.jloader.foo;

import com.ess.jloader.loader.PackClassLoader;
import com.ess.jloader.packer.Config;
import com.ess.jloader.packer.JarPacker;
import com.ess.jloader.packer.tests.TestUtils;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

/**
 * @author Sergey Evdokimov
 */
public class UnpackTime {

    private static List<String> loadClassesList(File jar) throws IOException {
        JarInputStream in = new JarInputStream(new BufferedInputStream(new FileInputStream(jar)));

        try {
            List<String> res = new ArrayList<String>();

            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String name = entry.getName();

                if (name.endsWith(".class")) {
                    res.add(name.substring(0, name.length() - ".class".length()));
                }
            }

            return res;
        } finally {
            in.close();
        }
    }

    @Test
    public void testGetUnpackTime() throws IOException, ClassNotFoundException {
        File guavaLoader = TestUtils.getJarByMarker("loadGuava.marker.txt");
        File guava = TestUtils.getJarByMarker("com/google/common/base/Objects.class");

        //JOptionPane.showMessageDialog(null, "Click Ok to continue");

        long time = System.currentTimeMillis();

        JarPacker packer = new JarPacker(new Config());
        packer.addJar(guavaLoader);
        packer.addJar(guava);

        File tempFile = TestUtils.createTmpPackFile("packedGuavaLoader-");
        packer.writeResult(tempFile);

        System.out.println("Packing time: " + (System.currentTimeMillis() - time));

        long srcSize = guava.length() + guavaLoader.length();
        System.out.printf("Src: %d, Result: %d,  (%d%%)\n", srcSize, tempFile.length(), tempFile.length() * 100 / srcSize);


        List<String> classes = loadClassesList(guava);
        Collections.shuffle(classes, new Random(485590345345L));

        long startTime = System.currentTimeMillis();

        PackClassLoader loader = new PackClassLoader(null, tempFile);
        for (String className : classes) {
            loader.unpackClass(className);
        }

        System.out.println("Time: " + (System.currentTimeMillis() - startTime));

    }

}
