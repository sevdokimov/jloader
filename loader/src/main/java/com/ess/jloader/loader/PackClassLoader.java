package com.ess.jloader.loader;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Sergey Evdokimov
 */
public class PackClassLoader extends ClassLoader implements Closeable {

    private ZipFile zip;

    public PackClassLoader(ClassLoader parent, File packFile) throws IOException {
        super(parent);
        zip = new ZipFile(packFile);
    }

    public PackClassLoader(File packFile) throws IOException {
        this(getSystemClassLoader(), packFile);
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

            data[0] = (byte) 0xCA;
            data[1] = (byte) 0xFE;
            data[2] = (byte) 0xBA;
            data[3] = (byte) 0xBE;

            return defineClass(name, data, 0, data.length);
        } catch (IOException e) {
            throw new ClassNotFoundException("", e);
        }
    }

    public void close() throws IOException {
        zip.close();
    }
}
