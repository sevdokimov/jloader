package com.ess.jloader.packer.tests;

import com.ess.jloader.loader.PackClassLoader;
import com.ess.jloader.packer.Config;
import com.ess.jloader.packer.JarPacker;
import org.junit.Test;

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

        long time = System.currentTimeMillis();

        JarPacker packer = new JarPacker(new Config());
        packer.addJar(guavaLoader);
        packer.addJar(guava);

        File tempFile = TestUtils.createTmpPAckFile("packedGuavaLoader-");
        packer.writeResult(tempFile);

        System.out.println("Packing time: " + (System.currentTimeMillis() - time));

        URLClassLoader plainClassLoader = URLClassLoader.newInstance(new URL[]{guavaLoader.toURI().toURL(), guava.toURI().toURL()}, null);

        long plainExecuteTime = getExecuteTime(plainClassLoader);
        long packedExecuteTime = getExecuteTime(new PackClassLoader(null, tempFile));
        System.out.printf("Plain: %d, packed: %d, (res: %d%%)\n", plainExecuteTime, packedExecuteTime, (packedExecuteTime - plainExecuteTime) * 100/plainExecuteTime);
    }

    private long getExecuteTime(ClassLoader loader) throws Exception {
        long time = System.currentTimeMillis();

        TestUtils.runJar(loader);

        return System.currentTimeMillis() - time;
    }

}
