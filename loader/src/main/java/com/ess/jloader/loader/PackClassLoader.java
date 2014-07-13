package com.ess.jloader.loader;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Sergey Evdokimov
 */
public class PackClassLoader extends ClassLoader implements Closeable {

    private ZipFile zip;

    public PackClassLoader(File packFile) throws IOException {
        zip = new ZipFile(packFile);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
//        System.out.println("Loading class \"" + name + "\"...");
        String classFileName = name.replace('.', '/').concat(".class");

        try {
            ZipEntry entry = zip.getEntry(classFileName);
            if (entry == null) throw new ClassNotFoundException();

            byte[] data = new byte[zip.size()];
            InputStream inputStream = zip.getInputStream(entry);

            new DataInputStream(inputStream).readFully(data);

            return defineClass(name, data, 0, data.length);
        } catch (IOException e) {
            throw new ClassNotFoundException("", e);
        }
    }

    public void close() throws IOException {
        zip.close();
    }
}
