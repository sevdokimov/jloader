package com.ess.jloader.loader;

import com.ess.jloader.utils.HuffmanInputStream;
import com.ess.jloader.utils.HuffmanUtils;
import com.ess.jloader.utils.OpenByteOutputStream;
import com.ess.jloader.utils.Utils;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.util.PriorityQueue;
import java.util.zip.*;

/**
 * @author Sergey Evdokimov
 */
public class PackClassLoader extends ClassLoader implements Closeable {

    public static final String METADATA_ENTRY_NAME = "META-INF/literals.data";

    private final HuffmanUtils.TreeElement packedStrHuffmanTree;

    private final int[] versions = new int[8];

    private final byte[] dictionary;

    private final ClassLoader delegateClassLoader;

    public PackClassLoader(ClassLoader parent, File packFile) throws IOException {
        this(parent, new URLClassLoader(new URL[]{packFile.toURI().toURL()}));
    }

    public PackClassLoader(ClassLoader parent, ClassLoader delegate) throws IOException {
        super(parent);

        delegateClassLoader = delegate;

        DataInputStream inputStream = new DataInputStream(new BufferedInputStream(delegate.getResourceAsStream(METADATA_ENTRY_NAME)));

        try {
            if (inputStream.readByte() != Utils.MAGIC) throw new RuntimeException();

            if (inputStream.readByte() != Utils.PACKER_VERSION) throw new RuntimeException();

            for (int i = 0; i < 8; i++) {
                versions[i] = inputStream.readInt();
            }

            int packedStringsCount = inputStream.readInt();
            byte[][] packedStrings = new byte[packedStringsCount][];
            for (int i = 0; i < packedStringsCount; i++) {
                int len = inputStream.readUnsignedShort();
                packedStrings[i] = new byte[len];
                inputStream.readFully(packedStrings[i]);
            }

            // Build Huffman tree
            PriorityQueue<HuffmanUtils.TreeElement> queue = new PriorityQueue<HuffmanUtils.TreeElement>();
            for (int i = 0; i < packedStringsCount; i++) {
                int count = inputStream.readUnsignedShort();
                queue.add(new HuffmanUtils.Leaf(count, packedStrings[i]));
            }
            packedStrHuffmanTree = HuffmanUtils.buildHuffmanTree(queue);

            int dictionarySize = inputStream.readUnsignedShort();
            dictionary = new byte[dictionarySize];
            inputStream.readFully(dictionary);
        }
        finally {
            inputStream.close();
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String jvmClassName = name.replace('.', '/');
        String classFileName = jvmClassName.concat(".c");

        try {
            InputStream inputStream = delegateClassLoader.getResourceAsStream(classFileName);
            if (inputStream == null) throw new ClassNotFoundException();

            inputStream = new BufferedInputStream(inputStream);

            try {
                DataInputStream in = new DataInputStream(inputStream);

                int flags = in.readInt();
                int size;

                if ((flags & Utils.F_LONG_CLASS) == 0) {
                    size = in.readUnsignedShort();
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
                int constCount = readSmallShort3(in);
                buffer.putShort((short) constCount);

                int utfCount = readLimitedShort(in, constCount);
                int firstUtfIndex = constCount - utfCount;

                int classCount = readSmallShort3(in);
                int nameAndTypeCount = readSmallShort3(in);

                buffer.put((byte) 7);
                buffer.putShort((short) firstUtfIndex); // Class name;

                for (int i = 1; i < classCount; i++) {
                    buffer.put((byte) 7);

                    int utfIndex = readLimitedShort(in, utfCount);

                    buffer.putShort((short) (utfIndex + firstUtfIndex));
                }

                int packedStrCount = readLimitedShort(in, utfCount);

                OpenByteOutputStream utfBufferArray = new OpenByteOutputStream();
                DataOutputStream utfOutput = new DataOutputStream(utfBufferArray);

                // Generated utf
                utfOutput.write(1);
                utfOutput.writeUTF(jvmClassName);

                // Packed utf
                HuffmanInputStream<byte[]> huffmanInputStream = new HuffmanInputStream<byte[]>(inputStream, packedStrHuffmanTree);
                for (int i = 0; i < packedStrCount; i++) {
                    utfOutput.write((byte) 1);
                    byte[] str = huffmanInputStream.read();
                    utfOutput.writeShort((short) str.length);
                    utfOutput.write(str);
                }

                // Compressed data
                Inflater inflater = new Inflater(true);
                inflater.setDictionary(dictionary);
                InflaterInputStream defIn = new InflaterInputStream(inputStream, inflater);
                DataInputStream defDataIn = new DataInputStream(new BufferedInputStream(defIn));

                skipConstTableTail(buffer, defDataIn, constCount - 1 - classCount - nameAndTypeCount - utfCount, firstUtfIndex, utfCount);

                for (int i = 0; i < nameAndTypeCount; i++) {
                    buffer.put((byte) 12); // ClassWriter.NAME_TYPE
                    buffer.putShort((short) (readLimitedShort(defDataIn, utfCount) + firstUtfIndex));
                    buffer.putShort((short) (readLimitedShort(defDataIn, utfCount) + firstUtfIndex));
                }

                utfBufferArray.writeTo(buffer);

                for (int i = utfCount - 1 - packedStrCount; --i >= 0; ) {
                    buffer.put((byte) 1);
                    int utfSize = defDataIn.readUnsignedShort();
                    buffer.putShort((short) utfSize);
                    defDataIn.readFully(array, buffer.position(), utfSize);
                    buffer.position(buffer.position() + utfSize);
                }

                int accessFlags = defDataIn.readShort();
                buffer.putShort((short) accessFlags);

                buffer.putShort((short) 1); // this class name index
                buffer.putShort((short) 2); // super class name index

                defDataIn.readFully(array, buffer.position(), size - buffer.position());

                return defineClass(name, array, 0, size);
            } finally {
                inputStream.close();
            }
        } catch (IOException e) {
            throw new ClassNotFoundException("", e);
        }
    }

    private int readLimitedShort(DataInputStream in, int limit) throws IOException {
        if (limit < 256) {
            if (limit == 0) {
                return 0;
            }
            return in.readUnsignedByte();
        }
        else {
            return in.readUnsignedShort();
        }
    }

    private int readSmallShort3(DataInputStream in) throws IOException {
        int x = in.readUnsignedByte();
        if (x <= 251) {
            return x;
        }

        if (x == 255) {
            return in.readUnsignedShort();
        }

        return (((x - 251) << 8) + in.readUnsignedByte()) - 4;
    }

    @Override
    protected URL findResource(String name) {
        return delegateClassLoader.getResource(name);
    }

    private void skipConstTableTail(ByteBuffer buffer, DataInputStream in, int count, int firstUtfIndex, int utfCount) throws IOException {
        byte[] array = buffer.array();

        for (int i = 0; i < count; i++) {
            int tag = in.read();
            buffer.put((byte) tag);

            switch (tag) {
                case 9: // ClassWriter.FIELD:
                case 10: // ClassWriter.METH:
                case 11: // ClassWriter.IMETH:
                case 3: // ClassWriter.INT:
                case 4: // ClassWriter.FLOAT:
                case 18: // ClassWriter.INDY:
                    in.readFully(array, buffer.position(), 4);
                    buffer.position(buffer.position() + 4);
                    break;

                case 5:// ClassWriter.LONG:
                case 6: // ClassWriter.DOUBLE:
                    in.readFully(array, buffer.position(), 8);
                    buffer.position(buffer.position() + 8);
                    ++i;
                    break;

                case 15: // ClassWriter.HANDLE:
                    in.readFully(array, buffer.position(), 3);
                    buffer.position(buffer.position() + 3);
                    break;

                case 7: // ClassWriter.CLASS
                case 8: // ClassWriter.STR
                case 16: // ClassWriter.MTYPE
                    int index = readLimitedShort(in, utfCount);
                    buffer.putShort((short) (index + firstUtfIndex));
                    break;

                default:
                    throw new RuntimeException();
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (delegateClassLoader instanceof Closeable) {
            ((Closeable) delegateClassLoader).close();
        }
    }
}
