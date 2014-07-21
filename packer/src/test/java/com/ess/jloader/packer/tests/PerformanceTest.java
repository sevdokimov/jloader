package com.ess.jloader.packer.tests;

import com.ess.jloader.loader.PackClassLoader;
import com.ess.jloader.packer.Config;
import com.ess.jloader.packer.JarPacker;
import org.junit.Test;

import javax.swing.*;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author Sergey Evdokimov
 */
public class PerformanceTest {

    @Test
    public void testPerformance() throws Exception {
        File guavaLoader = TestUtils.getJarByMarker("loadGuava.marker.txt");
        File guava = TestUtils.getJarByMarker("com/google/common/base/Objects.class");

        //JOptionPane.showMessageDialog(null, "Click Ok to continue");

        long time = System.currentTimeMillis();

        JarPacker packer = new JarPacker(new Config());
        packer.addJar(guavaLoader);
        packer.addJar(guava);

        File tempFile = TestUtils.createTmpPAckFile("packedGuavaLoader-");
        packer.writeResult(tempFile);

        System.out.println("Packing time: " + (System.currentTimeMillis() - time));

        getExecuteTime(URLClassLoader.newInstance(new URL[]{guavaLoader.toURI().toURL(), guava.toURI().toURL()}, null));

        long packedExecuteTime = getExecuteTime(new PackClassLoader(null, tempFile));

        URLClassLoader plainClassLoader = URLClassLoader.newInstance(new URL[]{guavaLoader.toURI().toURL(), guava.toURI().toURL()}, null);
        long plainExecuteTime = getExecuteTime(plainClassLoader);

        System.out.printf("Plain: %d, packed: %d, (res: %d%%)\n", plainExecuteTime, packedExecuteTime, (packedExecuteTime - plainExecuteTime) * 100/plainExecuteTime);

        long srcSize = guava.length() + guavaLoader.length();
        System.out.printf("Src: %d, Result: %d,  (%d%%)", srcSize, tempFile.length(), tempFile.length() * 100 / srcSize);
    }

    private long getExecuteTime(ClassLoader loader) throws Exception {
        long time = System.currentTimeMillis();

        TestUtils.runJar(loader);

        return System.currentTimeMillis() - time;
    }

}
