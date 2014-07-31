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

    public static final boolean CHECK_LIMITS = false;

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

            try {
                Unpacker unpacker = new Unpacker(new BufferedInputStream(inputStream), jvmClassName);

                byte[] bytes = unpacker.unpack();

                return defineClass(name, bytes, 0, bytes.length);
            } finally {
                inputStream.close();
            }
        } catch (IOException e) {
            throw new ClassNotFoundException("", e);
        }
    }

    @Override
    protected URL findResource(String name) {
        return delegateClassLoader.getResource(name);
    }

    @Override
    public void close() throws IOException {
        if (delegateClassLoader instanceof Closeable) {
            ((Closeable) delegateClassLoader).close();
        }
    }

    private class Unpacker {

        private final InputStream inputStream;

        private DataInputStream in;

        private final String className;

        private int flags;

        private ByteBuffer buffer;

        private int utfCount;
        private int firstUtfIndex;

        private int classCount;

        private int[] predefinedUtfIndexes;

        public Unpacker(InputStream inputStream, String className) {
            this.inputStream = inputStream;
            this.in = new DataInputStream(inputStream);
            this.className = className;
        }

        public byte[] unpack() throws IOException {
            flags = in.readUnsignedShort();
            int predefinedStrings = in.readUnsignedShort();

            int size;

            if ((flags & Utils.F_LONG_CLASS) == 0) {
                size = in.readUnsignedShort();
            }
            else {
                size = in.readInt();
            }

            ByteBuffer buffer = ByteBuffer.allocate(size);
            this.buffer = buffer;
            byte[] array = buffer.array();

            // Magic
            buffer.putInt(0xCAFEBABE);

            // Version
            buffer.putInt(versions[flags & 7]);

            // Const count
            int constCount = readSmallShort3(in);
            buffer.putShort((short) constCount);

            utfCount = readLimitedShort(in, constCount);
            firstUtfIndex = constCount - utfCount;

            classCount = readSmallShort3(in);
            int nameAndTypeCount = readSmallShort3(in);

            buffer.put((byte) 7);
            buffer.putShort((short) firstUtfIndex); // Class name;

            for (int i = 1; i < classCount; i++) {
                buffer.put((byte) 7);

                int utfIndex = readUtfIndex(in);
                buffer.putShort((short) utfIndex);
            }

            int packedStrCount = readLimitedShort(in, utfCount);

            OpenByteOutputStream utfBufferArray = new OpenByteOutputStream();
            DataOutputStream utfOutput = new DataOutputStream(utfBufferArray);

            // Generated utf
            utfOutput.write(1);
            utfOutput.writeUTF(className);
            int processedUtfCount = 1;

            processedUtfCount = extractPredefinedStrings(utfOutput, processedUtfCount, predefinedStrings);

            // Packed utf
            HuffmanInputStream<byte[]> huffmanInputStream = new HuffmanInputStream<byte[]>(inputStream, packedStrHuffmanTree);
            for (int i = 0; i < packedStrCount; i++) {
                utfOutput.write((byte) 1);
                byte[] str = huffmanInputStream.read();
                utfOutput.writeShort((short) str.length);
                utfOutput.write(str);
            }
            processedUtfCount += packedStrCount;

            // Compressed data
            Inflater inflater = new Inflater(true);
            inflater.setDictionary(dictionary);
            InflaterInputStream defIn = new InflaterInputStream(inputStream, inflater);
            DataInputStream defDataIn = new DataInputStream(new BufferedInputStream(defIn));

            skipConstTableTail(defDataIn, constCount - 1 - classCount - nameAndTypeCount - utfCount,
                    firstUtfIndex - nameAndTypeCount, nameAndTypeCount);

            for (int i = 0; i < nameAndTypeCount; i++) {
                buffer.put((byte) 12); // ClassWriter.NAME_TYPE
                buffer.putShort((short) readUtfIndex(defDataIn));
                buffer.putShort((short) readUtfIndex(defDataIn));
            }

            utfBufferArray.writeTo(buffer);

            for (int i = utfCount - processedUtfCount; --i >= 0; ) {
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

            // Process interfaces
            processInterfaces(defDataIn);

            processFields(defDataIn);
            processMethods(defDataIn);
            processClassAttr(defDataIn);

            assert !buffer.hasRemaining();

            return array;
        }

        private int extractPredefinedStrings(DataOutputStream utfOutput, int currentUtfIndex, int predefinedStrFlags) throws IOException {
            int predefinedUtfCount = Utils.PREDEFINED_UTF.length;
            predefinedUtfIndexes = new int[predefinedUtfCount];

            predefinedStrFlags <<= 32 - predefinedUtfCount;

            for (int i = 0; i < predefinedUtfCount; i++) {
                if (predefinedStrFlags < 0) {
                    predefinedUtfIndexes[i] = currentUtfIndex++;

                    utfOutput.write(1);
                    utfOutput.writeUTF(Utils.PREDEFINED_UTF[i]);
                }

                predefinedStrFlags <<= 1;
            }

            return currentUtfIndex;
        }

        private void skipConstTableTail(DataInputStream in, int count,
                                        int firstNameAndTypeIndex, int nameAndTypeCount) throws IOException {
            ByteBuffer buffer = this.buffer;
            byte[] array = buffer.array();

            for (int i = 0; i < count; i++) {
                int tag = in.read();
                buffer.put((byte) tag);

                switch (tag) {
                    case 9: // ClassWriter.FIELD:
                    case 10: // ClassWriter.METH:
                    case 11: // ClassWriter.IMETH:
                        int classIndex = readLimitedShort(in, classCount - 1);
                        buffer.putShort((short) (classIndex + 1));
                        int nameAndTypeIndex = readLimitedShort(in, nameAndTypeCount - 1);
                        buffer.putShort((short) (nameAndTypeIndex + firstNameAndTypeIndex));
                        break;

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
                        buffer.putShort((short) (readUtfIndex(in)));
                        break;

                    default:
                        throw new RuntimeException();
                }
            }
        }

        private void processInterfaces(DataInputStream defDataIn) throws IOException {
            int interfaceCount = (flags >> Utils.F_INTERFACE_COUNT_SHIFT) & 3;
            if (interfaceCount == 3) {
                interfaceCount = defDataIn.readUnsignedByte();
            }
            buffer.putShort((short) interfaceCount);

            for (int i = 0; i < interfaceCount; i++) {
                int classIndex = readLimitedShort(defDataIn, classCount - 1) + 3;
                buffer.putShort((short) classIndex);
            }
        }

        private void processFields(DataInputStream defDataIn) throws IOException {
            int fieldCount = readSmallShort3(defDataIn);
            buffer.putShort((short) fieldCount);

            for (int i = 0; i < fieldCount; i++) {
                short accessFlags = defDataIn.readShort();
                buffer.putShort(accessFlags);

                int nameIndex = readUtfIndex(defDataIn);
                buffer.putShort((short) nameIndex);

                int descrIndex = readUtfIndex(defDataIn);
                buffer.putShort((short) descrIndex);

                int attrCount = readSmallShort3(defDataIn);
                buffer.putShort((short) attrCount);

                for (int j = 0; j < attrCount; j++) {
                    processAttr(defDataIn);
                }
            }
        }

        private void processMethods(DataInputStream defDataIn) throws IOException {
            int methodCount = readSmallShort3(defDataIn);
            buffer.putShort((short) methodCount);

            for (int i = 0; i < methodCount; i++) {
                short accessFlags = defDataIn.readShort();
                buffer.putShort(accessFlags);

                int nameIndex = readUtfIndex(defDataIn);
                buffer.putShort((short) nameIndex);

                int descrIndex = readUtfIndex(defDataIn);
                buffer.putShort((short) descrIndex);

                int attrCount = readSmallShort3(defDataIn);
                buffer.putShort((short) attrCount);

                for (int j = 0; j < attrCount; j++) {
                    processAttr(defDataIn);
                }
            }
        }

        private void processClassAttr(DataInputStream defDataIn) throws IOException {
            int attrCount = readSmallShort3(defDataIn);
            buffer.putShort((short) attrCount);

            for (int j = 0; j < attrCount; j++) {
                processAttr(defDataIn);
            }
        }

        private void processAttr(DataInputStream defDataIn) throws IOException {
            int nameIndex = readUtfIndex(defDataIn);
            buffer.putShort((short) nameIndex);

            int length = defDataIn.readInt();
            buffer.putInt(length);
            defDataIn.readFully(buffer.array(), buffer.position(), length);
            buffer.position(buffer.position() + length);
        }

        private int readUtfIndex(DataInputStream in) throws IOException {
            return firstUtfIndex + readLimitedShort(in, utfCount - 1);
        }

        private int readLimitedShort(DataInputStream in, int limit) throws IOException {
            if (CHECK_LIMITS) {
                int storedLimit = in.readUnsignedShort();
                if (storedLimit != limit) {
                    throw new RuntimeException();
                }
            }

            int res;
            if (limit < 256) {
                if (limit == 0) {
                    return 0;
                }
                res = in.readUnsignedByte();
            }
            else {
                res = in.readUnsignedShort();
            }

            assert res <= limit;

            return res;
        }

        private int readSmallShort3(DataInputStream in) throws IOException {
            if (CHECK_LIMITS) {
                if (in.readByte() != 0x73) throw new RuntimeException();
            }

            int x = in.readUnsignedByte();
            if (x <= 251) {
                return x;
            }

            if (x == 255) {
                return in.readUnsignedShort();
            }

            return (((x - 251) << 8) + in.readUnsignedByte()) - 4;
        }
    }
}
