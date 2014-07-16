package com.ess.jloader.loader;

import com.ess.jloader.utils.HuffmanInputStream;
import com.ess.jloader.utils.HuffmanUtils;
import com.ess.jloader.utils.OpenByteOutputStream;
import com.ess.jloader.utils.Utils;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.PriorityQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Sergey Evdokimov
 */
public class PackClassLoader extends ClassLoader implements Closeable {

    public static final String METADATA_ENTRY_NAME = "META-INF/literals.data";

    private ZipFile zip;

    private final String[] packedStrings;

    private final HuffmanUtils.TreeElement packedStrHuffmanTree;

    private final int[] versions = new int[8];

    public PackClassLoader(ClassLoader parent, File packFile) throws IOException {
        super(parent);
        zip = new ZipFile(packFile);

        ZipEntry entry = zip.getEntry(METADATA_ENTRY_NAME);
        if (entry == null) throw new RuntimeException();

        DataInputStream inputStream = new DataInputStream(zip.getInputStream(entry));
        try {
            if (inputStream.readByte() != Utils.MAGIC) throw new RuntimeException();

            if (inputStream.readByte() != Utils.PACKER_VERSION) throw new RuntimeException();

            for (int i = 0; i < 8; i++) {
                versions[i] = inputStream.readInt();
            }

            int packedStringsCount = inputStream.readInt();
            packedStrings = new String[packedStringsCount];
            for (int i = 0; i < packedStringsCount; i++) {
                packedStrings[i] = inputStream.readUTF();
            }

            // Build Huffman tree
            PriorityQueue<HuffmanUtils.TreeElement> queue = new PriorityQueue<HuffmanUtils.TreeElement>();
            for (int i = 0; i < packedStringsCount; i++) {
                int count = inputStream.readUnsignedShort();
                queue.add(new HuffmanUtils.Leaf(count, packedStrings[i]));
            }
            packedStrHuffmanTree = HuffmanUtils.buildHuffmanTree(queue);
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
                buffer.putInt(versions[flags & 7]);

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
                HuffmanInputStream<String> huffmanInputStream = new HuffmanInputStream<String>(inputStream, packedStrHuffmanTree);
                for (int i = 0; i < packedStrCount; i++) {
                    out.write(1);
                    out.writeUTF(huffmanInputStream.read());
                }

                buffer.position(buffer.position() + out.size());

                skipConstTableTail(buffer, in, constCount - 1 - packedStrCount - 2);

                int accessFlags = in.readShort();
                buffer.putShort((short) accessFlags);

                buffer.putShort((short) 2);

                in.readFully(array, buffer.position(), size - buffer.position());

                return defineClass(name, array, 0, size);
            } finally {
                inputStream.close();
            }
        } catch (IOException e) {
            throw new ClassNotFoundException("", e);
        }
    }

    private void skipConstTableTail(ByteBuffer buffer, DataInputStream in, int constCount) throws IOException {
        for (int i = 0; i < constCount; i++) {
            int tag = in.read();
            buffer.put((byte) tag);

            int size;
            switch (tag) {
                case 9: // ClassWriter.FIELD:
                case 10: // ClassWriter.METH:
                case 11: // ClassWriter.IMETH:
                case 3: // ClassWriter.INT:
                case 4: // ClassWriter.FLOAT:
                case 12: // ClassWriter.NAME_TYPE:
                case 18: // ClassWriter.INDY:
                    size = 4;
                    break;
                case 5:// ClassWriter.LONG:
                case 6: // ClassWriter.DOUBLE:
                    size = 8;
                    ++i;
                    break;
                case 1: // ClassWriter.UTF8:
                    size = in.readUnsignedShort();
                    buffer.putShort((short) size);
                    break;

                case 15: // ClassWriter.HANDLE:
                    size = 3;
                    break;
                // case ClassWriter.CLASS:
                // case ClassWriter.STR:
                // case ClassWriter.MTYPE
                default:
                    size = 2;
                    break;
            }

            in.readFully(buffer.array(), buffer.position(), size);
            buffer.position(buffer.position() + size);
        }
    }

    public void close() throws IOException {
        zip.close();
    }
}
