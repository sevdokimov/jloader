package com.ess.jloader.packer.tests;

import com.ess.jloader.packer.Config;
import com.ess.jloader.packer.JarPacker;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.io.File;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class PackJarsFromMavenRepositoriesTest {

    @Rule
    public TestName name = new TestName();

    @Test
    public void packJRuby() throws IOException, ClassNotFoundException {
        doTest("META-INF/maven/org.jruby/jrubyparser/pom.xml");
    }

    @Test
    public void packFreeMarker() throws IOException, ClassNotFoundException {
        doTest("freemarker/core/Assignment.class");
    }

    @Test
    public void packGroovyAll() throws IOException, ClassNotFoundException {
        doTest("org/codehaus/groovy/transform/stc/Receiver.class");
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
