package com.ess.jloader.loader;

import com.ess.jloader.utils.Utils;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Sergey Evdokimov
 */
public class PackClassLoader extends ClassLoader implements Closeable {

    public static final String METADATA_ENTRY_NAME = "META-INF/literals.data";

    private ZipFile zip;

    public PackClassLoader(ClassLoader parent, File packFile) throws IOException {
        super(parent);
        zip = new ZipFile(packFile);

        ZipEntry entry = zip.getEntry(METADATA_ENTRY_NAME);
        if (entry == null) throw new RuntimeException();

        DataInputStream inputStream = new DataInputStream(zip.getInputStream(entry));
        try {
            if (inputStream.readByte() != Utils.MAGIC) throw new RuntimeException();

            if (inputStream.readByte() != Utils.PACKER_VERSION) throw new RuntimeException();
        }
        finally {
            inputStream.close();
        }
    }

    public PackClassLoader(File packFile) throws IOException {
        this(getSystemClassLoader(), packFile);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String classFileName = name.replace('.', '/').concat(".c");

        try {
            ZipEntry entry = zip.getEntry(classFileName);
            if (entry == null) throw new ClassNotFoundException();

            InputStream inputStream = zip.getInputStream(entry);

            try {
                DataInputStream in = new DataInputStream(inputStream);

                int flags = in.readInt();
                int size;

                if ((flags & Utils.F_LONG_CLASS) == 0) {
                    size = in.readShort();
                }
                else {
                    size = in.readInt();
                }

                ByteBuffer buffer = ByteBuffer.allocate(size);
                byte[] array = buffer.array();

                buffer.putInt(0xCAFEBABE);

                in.readFully(array, 4, size - 4);

                return defineClass(name, array, 0, size);
            } finally {
                inputStream.close();
            }
        } catch (IOException e) {
            throw new ClassNotFoundException("", e);
        }
    }

    public void close() throws IOException {
        zip.close();
    }
}
