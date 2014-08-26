package com.ess.jloader.packer.tests;

import com.ess.jloader.packer.ClassDescriptor;
import com.ess.jloader.packer.Config;
import com.ess.jloader.packer.JarPacker;
import com.ess.jloader.packer.LiteralsCache;
import com.google.common.util.concurrent.AtomicLongMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runners.JUnit4;
import org.objectweb.asm.ClassReader;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/**
 * @author Sergey Evdokimov
 */
public class PackJarsFromMavenRepositoriesTest {

    @Rule
    public TestName name = new TestName();

    @Test
    public void packFreeMarker() throws IOException, ClassNotFoundException {
        doTest("freemarker/core/Assignment.class");
    }

    private void doTest(String marker) throws IOException {
        File sourceJar = TestUtils.getJarByMarker(marker);

        File packedJar = TestUtils.createTmpPackFile(name.getMethodName());

        JarPacker packer = new JarPacker(new Config());
        packer.addJar(sourceJar);
        packer.writeResult(packedJar);

        System.out.printf("Src: %d, Result: %d,  (%d%%)", sourceJar.length(), packedJar.length(), packedJar.length() * 100 / sourceJar.length());

        packer.checkResult(packedJar);
    }

}
