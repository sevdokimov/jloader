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

    private File getJarByMarker(String marker) {
        String path = Thread.currentThread().getContextClassLoader().getResource(marker).getPath();
        int idx = path.lastIndexOf("!/");
        assert idx > 0;

        path = path.substring(0, idx);
        if (path.startsWith("file:")) {
            path = path.substring("file:".length());
        }

        return new File(path);
    }

    private void testProject(String marker) throws Exception {
        File src = getJarByMarker(marker);

        File tempFile = new File(System.getProperty("java.io.tmpdir") + "/packed-" + marker.substring(0, marker.indexOf('.')) + ".j");

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
