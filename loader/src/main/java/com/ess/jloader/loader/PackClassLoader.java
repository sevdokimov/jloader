package com.ess.jloader.loader;

import com.ess.jloader.utils.OpenByteOutputStream;
import com.ess.jloader.utils.Utils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Sergey Evdokimov
 */
public class PackClassLoader extends ClassLoader implements Closeable {

    public static final String METADATA_ENTRY_NAME = "META-INF/literals.data";

    private ZipFile zip;

    private String[] packedStrings;

    public PackClassLoader(ClassLoader parent, File packFile) throws IOException {
        super(parent);
        zip = new ZipFile(packFile);

        ZipEntry entry = zip.getEntry(METADATA_ENTRY_NAME);
        if (entry == null) throw new RuntimeException();

        DataInputStream inputStream = new DataInputStream(zip.getInputStream(entry));
        try {
            if (inputStream.readByte() != Utils.MAGIC) throw new RuntimeException();

            if (inputStream.readByte() != Utils.PACKER_VERSION) throw new RuntimeException();

            int packedStringsCount = inputStream.readInt();
            packedStrings = new String[packedStringsCount];
            for (int i = 0; i < packedStringsCount; i++) {
                packedStrings[i] = inputStream.readUTF();
            }
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
        String jvmClassName = name.replace('.', '/');
        String classFileName = jvmClassName.concat(".c");

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

                // Magic
                buffer.putInt(0xCAFEBABE);

                // Version
                buffer.putInt(in.readInt());

                // Const count
                int constCount = in.readUnsignedShort();
                buffer.putShort((short) constCount);

                // Const table
                int packedStrCount = in.readUnsignedShort();
                DataOutputStream out = new DataOutputStream(OpenByteOutputStream.wrap(array, buffer.position()));

                // Class name
                out.write(1);
                out.writeUTF(jvmClassName);
                out.write(7);
                out.writeShort(1);

                // Packed String Constants

                for (int i = 0; i < packedStrCount; i++) {
                    int strIndex = in.readInt();

                    out.write(1);
                    out.writeUTF(packedStrings[strIndex]);
                }

                buffer.position(buffer.position() + out.size());

                in.readFully(array, buffer.position(), size - buffer.position());

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
