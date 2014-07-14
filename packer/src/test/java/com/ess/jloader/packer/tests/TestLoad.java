package com.ess.jloader.packer.tests;

import com.ess.jloader.loader.PackClassLoader;
import com.ess.jloader.packer.JarPacker;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Method;

/**
 * @author Sergey Evdokimov
 */
public class TestLoad {

    @Test
    public void testProject1() throws Exception {
        testProject("project1.marker.txt");
    }

    private void testProject(String marker) throws Exception {
        File src = TestUtils.getJarByMarker(marker);

        File tempFile = TestUtils.createTmpPAckFile("packed-" + marker.substring(0, marker.indexOf('.')));

        JarPacker.pack(src, tempFile);

        ClassLoader l = new PackClassLoader(tempFile);
        Class s = l.loadClass("Main");
        checkClass(s);

        System.out.println("jar: " + src.length() + ", pack: " + tempFile.length());
    }

    private void checkClass(Class aClass) throws Exception {
        Method main = aClass.getMethod("main", new Class[]{String[].class});

        main.invoke(null, (Object)new String[0]);
    }


//    @Test
//    public void pack() throws IOException {
//        Pack200.Packer packer = Pack200.newPacker();
//
//        ByteArrayOutputStream out = new ByteArrayOutputStream();
//        packer.pack(new JarFile(src), out);
//
//        JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(new File("/tmp/pack200.jar")));
//
//        try {
//            Pack200.newUnpacker().unpack(new ByteArrayInputStream(out.toByteArray()), jarOut);
//        } finally {
//            jarOut.close();
//        }
//    }

}
