package com.ess.jloader.packer.tests;

import com.ess.jloader.loader.PackClassLoader;
import com.ess.jloader.packer.Config;
import com.ess.jloader.packer.JarPacker;
import com.google.common.base.Throwables;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
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

        final File tempFile = TestUtils.createTmpPackFile("packedGuavaLoader-");
        packer.writeResult(tempFile);

        System.out.println("Packing time: " + (System.currentTimeMillis() - time));

        long srcSize = guava.length() + guavaLoader.length();
        System.out.printf("Src: %d, Result: %d,  (%d%%)\n", srcSize, tempFile.length(), tempFile.length() * 100 / srcSize);

        getExecuteTime(URLClassLoader.newInstance(new URL[]{guavaLoader.toURI().toURL(), guava.toURI().toURL()}, null));

        long packedExecuteTime = getExecuteTime(new ClassLoaderFactory() {
            @Override
            public ClassLoader create() {
                try {
                    return new PackClassLoader(null, tempFile);
                } catch (IOException e) {
                    throw Throwables.propagate(e);
                }
            }
        });

        URLClassLoader plainClassLoader = URLClassLoader.newInstance(new URL[]{guavaLoader.toURI().toURL(), guava.toURI().toURL()}, null);
        long plainExecuteTime = getExecuteTime(plainClassLoader);

        System.out.printf("Plain: %d, packed: %d, (res: %d%%)\n", plainExecuteTime, packedExecuteTime, (packedExecuteTime - plainExecuteTime) * 100/plainExecuteTime);
    }

    private long getExecuteTime(final ClassLoader loader) throws Exception {
        return getExecuteTime(new ClassLoaderFactory() {
            @Override
            public ClassLoader create() {
                return loader;
            }
        });
    }

    private long getExecuteTime(ClassLoaderFactory factory) throws Exception {
        long time = System.currentTimeMillis();

        TestUtils.runJar(factory.create());

        return System.currentTimeMillis() - time;
    }

    private static interface ClassLoaderFactory {
        ClassLoader create();
    }

}
