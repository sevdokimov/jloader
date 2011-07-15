package com.ess.jloader.packer.tests;

import com.ess.jloader.loader.ArrayUtil;
import com.ess.jloader.loader.PackClassLoader;
import com.ess.jloader.packer.JarPacker;
import org.junit.Test;

import java.io.*;
import java.lang.reflect.Method;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;

/**
 * @author Sergey Evdokimov
 */
public class TestLoad {

    private static final File src = new File("/media/A2B89412B893E2D9/w/jloader/samples/TestProject/target/TestProject-1.0.jar");

    @Test
    public void testXxx() throws Exception {
        File tempFile = File.createTempFile("packTest", ".pack");

        try {
            JarPacker.pack(src, tempFile);

            ClassLoader l = new PackClassLoader(tempFile);
            Class s = l.loadClass("Zzz");
            checkClass(s);

            System.out.println("jar: " + src.length() + ", pack: " + tempFile.length());
        } finally {
            tempFile.delete();
        }
    }

    private void checkClass(Class aClass) throws Exception {
        Method hello = aClass.getMethod("hello", ArrayUtil.EMPTY_CLASS_ARRAY);
        Object res = hello.invoke(null);
        assert "XXX".equals(res);
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
