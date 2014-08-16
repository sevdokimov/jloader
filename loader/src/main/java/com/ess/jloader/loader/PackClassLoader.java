package com.ess.jloader.loader;

import com.ess.jloader.utils.*;

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

    private final URLClassLoader delegateClassLoader;

    private final ZipFile zipFile;

    public PackClassLoader(ClassLoader parent, File packFile) throws IOException {
        super(parent);

        delegateClassLoader = new URLClassLoader(new URL[]{packFile.toURI().toURL()});

        zipFile = new ZipFile(packFile);

      //  delegateClassLoader = delegate;
        ZipEntry entry = zipFile.getEntry(METADATA_ENTRY_NAME);

        boolean allRight = false;

        DataInputStream inputStream = new DataInputStream(new BufferedInputStream(zipFile.getInputStream(entry)));

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
                int count = readSmallShort3(inputStream);
                queue.add(new HuffmanUtils.Leaf(count, packedStrings[i]));
            }
            packedStrHuffmanTree = HuffmanUtils.buildHuffmanTree(queue);

            int dictionarySize = inputStream.readUnsignedShort();
            dictionary = new byte[dictionarySize];
            inputStream.readFully(dictionary);

            allRight = true;
        }
        finally {
            inputStream.close();

            if (!allRight) {
                zipFile.close();
            }
        }
    }

    public byte[] unpackClass(String jvmClassName) throws ClassNotFoundException {
        String classFileName = jvmClassName.concat(".class");

        try {
            ZipEntry entry = zipFile.getEntry(classFileName);
            if (entry == null) throw new ClassNotFoundException();

            InputStream inputStream = zipFile.getInputStream(entry);

            try {
                DataInputStream dataInputStream = new DataInputStream(inputStream);
                int plainSize = dataInputStream.readUnsignedShort();
                byte[] plainData = new byte[plainSize];
                dataInputStream.readFully(plainData);

                BitInputStream in = new BitInputStream(new ByteArrayInputStream(plainData));

                Inflater inflater = new Inflater(true);
                inflater.setDictionary(dictionary);
                InflaterInputStream defIn = new InflaterInputStream(new BufferedInputStream(inputStream), inflater);

                Unpacker unpacker = new Unpacker(in, defIn, jvmClassName);

                return unpacker.unpack();
            } finally {
                inputStream.close();
            }
        } catch (IOException e) {
            throw new ClassNotFoundException("", e);
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String jvmClassName = name.replace('.', '/');

        byte[] classData = unpackClass(jvmClassName);
        return defineClass(name, classData, 0, classData.length);
    }

    @Override
    protected URL findResource(String name) {
        return delegateClassLoader.getResource(name);
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
    }

    static int readSmallShort3(DataInput in) throws IOException {
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

    private class Unpacker {

        private final DataInputStream defDataIn;

        private BitInputStream in;

        private final String className;

        private boolean hasSourceFileAttr;

        private ByteBuffer buffer;
        private OpenByteOutputStream bufferByteOutput;
        private DataOutputStream bufferDataOutput;

        private int utfCount;
        private LimitNumberReader utfLimiter;
        private int firstUtfIndex;

        private int classCount;
        private LimitNumberReader constClassesLimiter;

        private int fieldConstCount;
        private int firstFieldIndex;
        private int methodConstCount;
        private int firstMethodIndex;
        private int imethodConstCount;
        private int firstImethodIndex;

        private int[] predefinedUtfIndexes;

        private int sourceFileIndex;

        private int anonymousClassCount;
        private int firstAnonymousNameIndex;

        public Unpacker(BitInputStream in, InputStream defIn, String className) {
            this.in = in;
            this.defDataIn = new DataInputStream(new BufferedInputStream(defIn));

            this.className = className;
        }

        public byte[] unpack() throws IOException {
            hasSourceFileAttr = in.readBoolean();

            ByteBuffer buffer = ByteBuffer.allocate(readClassSize());
            this.buffer = buffer;
            byte[] array = buffer.array();
            bufferByteOutput = new OpenByteOutputStream(array);
            bufferDataOutput = new DataOutputStream(bufferByteOutput);

            // Magic
            buffer.putInt(0xCAFEBABE);

            // Version
            buffer.putInt(versions[in.readBits(3)]);

            anonymousClassCount = in.readSmall_0_3_8_16();

            // Const count
            int constCount = readSmallShort3(in);
            buffer.putShort((short) constCount);

            LimitNumberReader constCountLimiter = new LimitNumberReader(constCount);

            utfCount = constCountLimiter.read(in);
            utfLimiter = new LimitNumberReader(utfCount - 1);

            firstUtfIndex = constCount - utfCount;

            classCount = utfLimiter.read(in);
            constClassesLimiter = new LimitNumberReader(classCount - 1);

            fieldConstCount = readSmallShort3(in);
            imethodConstCount = readSmallShort3(in);
            methodConstCount = readSmallShort3(in);

            int nameAndTypeCount = readSmallShort3(in);

            firstMethodIndex = firstUtfIndex - nameAndTypeCount - methodConstCount;
            firstImethodIndex = firstMethodIndex - imethodConstCount;
            firstFieldIndex = firstImethodIndex - fieldConstCount;

            buffer.put((byte) 7);
            buffer.putShort((short) firstUtfIndex); // Class name;

            for (int i = 1; i < classCount; i++) {
                buffer.put((byte) 7);

                int utfIndex = readUtfIndexPlain();
                buffer.putShort((short) utfIndex);
            }

            int packedStrCount = utfLimiter.read(in);

            skipConstTableTail(constCount - 1 - classCount
                            - fieldConstCount - imethodConstCount - methodConstCount
                            - nameAndTypeCount
                            - utfCount);

            int firstNameAndType = firstUtfIndex - nameAndTypeCount;

            LimitNumberReader nameAndTypeLimiter = new LimitNumberReader(nameAndTypeCount - 1);

            for (int i = 0; i < fieldConstCount; i++) {
                buffer.put((byte) 9);
                buffer.putShort((short) (constClassesLimiter.read(in) + 1));
                buffer.putShort((short) (nameAndTypeLimiter.read(in) + firstNameAndType));
            }

            for (int i = 0; i < imethodConstCount; i++) {
                buffer.put((byte) 11);
                buffer.putShort((short) (constClassesLimiter.read(in) + 1));
                buffer.putShort((short) (nameAndTypeLimiter.read(in) + firstNameAndType));
            }

            for (int i = 0; i < methodConstCount; i++) {
                buffer.put((byte) 10);
                buffer.putShort((short) (constClassesLimiter.read(in) + 1));
                buffer.putShort((short) (nameAndTypeLimiter.read(in) + firstNameAndType));
            }

            for (int i = 0; i < nameAndTypeCount; i++) {
                buffer.put((byte) 12); // ClassWriter.NAME_TYPE
                buffer.putShort((short) readUtfIndexPlain());
                buffer.putShort((short) readUtfIndexPlain());
            }

            // Generated utf
            buffer.put((byte) 1);
            putUtf(className);
            int processedUtfCount = 1;

            processedUtfCount = extractPredefinedStrings(processedUtfCount);

            if (hasSourceFileAttr) {
                sourceFileIndex = processedUtfCount;

                buffer.put((byte) 1);
                buffer.put(Utils.C_SourceFile);
                buffer.put((byte) 1);
                putUtf(Utils.generateSourceFileName(className));

                processedUtfCount += 2;
            }

            firstAnonymousNameIndex = processedUtfCount;
            for (int i = 1; i <= anonymousClassCount; i++) {
                buffer.put((byte) 1);
                putUtf(className + '$' + i);

                processedUtfCount++;
            }

            // Packed utf
            HuffmanInputStream<byte[]> huffmanInputStream = new HuffmanInputStream<byte[]>(in, packedStrHuffmanTree);
            for (int i = 0; i < packedStrCount; i++) {
                buffer.put((byte) 1);
                byte[] str = huffmanInputStream.read();
                buffer.putShort((short) str.length);
                buffer.put(str);
            }
            processedUtfCount += packedStrCount;

            for (int i = utfCount - processedUtfCount; --i >= 0; ) {
                buffer.put((byte) 1);
                int utfSize = defDataIn.readUnsignedShort();
                buffer.putShort((short) utfSize);
                Utils.read(defDataIn, buffer, utfSize);
            }

            int accessFlags = defDataIn.readShort();
            buffer.putShort((short) accessFlags);

            buffer.putShort((short) 1); // this class name index
            buffer.putShort((short) 2); // super class name index

            // Process interfaces
            processInterfaces();

            processFields();
            processMethods();
            processClassAttr();

            assert !buffer.hasRemaining() : className;

            return array;
        }

        private void putUtf(String s) throws IOException {
            bufferByteOutput.setPosition(buffer.position());
            bufferDataOutput.writeUTF(s);

            buffer.position(bufferByteOutput.size());
        }

        private int readClassSize() throws IOException {
            int size = in.readShort();
            if (size < 0) {
                size = (-size) << 15 | in.readShort();
            }

            return size;
        }

        private int extractPredefinedStrings(int currentUtfIndex) throws IOException {
            int predefinedUtfCount = Utils.PREDEFINED_UTF.length;
            predefinedUtfIndexes = new int[predefinedUtfCount];

            for (int i = 0; i < predefinedUtfCount; i++) {
                if (in.readBoolean()) {
                    predefinedUtfIndexes[i] = currentUtfIndex + firstUtfIndex;

                    currentUtfIndex++;

                    buffer.put((byte) 1);
                    int utfStart = Utils.PREDEFINED_UTF_BYTE_INDEXES[i];
                    buffer.put(Utils.PREDEFINED_UTF_BYTES, utfStart, Utils.PREDEFINED_UTF_BYTE_INDEXES[i + 1] - utfStart);
                }
            }

            return currentUtfIndex;
        }

        private void skipConstTableTail(int count) throws IOException {
            ByteBuffer buffer = this.buffer;

            for (int i = 0; i < count; i++) {
                int tag = defDataIn.read();
                buffer.put((byte) tag);

                switch (tag) {
                    case 3: // ClassWriter.INT:
                    case 4: // ClassWriter.FLOAT:
                    case 18: // ClassWriter.INDY:
                        Utils.read(defDataIn, buffer, 4);
                        break;

                    case 5:// ClassWriter.LONG:
                    case 6: // ClassWriter.DOUBLE:
                        Utils.read(defDataIn, buffer, 8);
                        ++i;
                        break;

                    case 15: // ClassWriter.HANDLE:
                        if (true) throw new UnsupportedOperationException();
                        Utils.read(defDataIn, buffer, 3);
                        break;

                    case 8: // ClassWriter.STR
                    case 16: // ClassWriter.MTYPE
                        buffer.putShort((short) (readUtfIndexPlain()));
                        break;

                    default:
                        throw new RuntimeException();
                }
            }
        }

        private void processInterfaces() throws IOException {
            int interfaceCount = in.readSmall_0_3_8_16();
            buffer.putShort((short) interfaceCount);

            for (int i = 0; i < interfaceCount; i++) {
                int classIndex = constClassesLimiter.read(in) + 1;
                buffer.putShort((short) classIndex);
            }
        }

        private void processFields() throws IOException {
            int fieldCount = readSmallShort3(defDataIn);
            buffer.putShort((short) fieldCount);

            for (int i = 0; i < fieldCount; i++) {
                short accessFlags = defDataIn.readShort();
                buffer.putShort(accessFlags);

                int nameIndex = readUtfIndexDef();
                buffer.putShort((short) nameIndex);

                int descrIndex = readUtfIndexDef();
                buffer.putShort((short) descrIndex);

                int attrCount = readSmallShort3(defDataIn);
                buffer.putShort((short) attrCount);

                for (int j = 0; j < attrCount; j++) {
                    processAttr();
                }
            }
        }

        private void processMethods() throws IOException {
            int methodCount = readSmallShort3(defDataIn);
            buffer.putShort((short) methodCount);

            for (int i = 0; i < methodCount; i++) {
                int accessFlags = defDataIn.readShort();
                buffer.putShort((short) accessFlags);

                int nameIndex = readUtfIndexDef();
                buffer.putShort((short) nameIndex);

                int descrIndex = readUtfIndexDef();
                buffer.putShort((short) descrIndex);

                int attrCount = readSmallShort3(defDataIn);
                buffer.putShort((short) attrCount);

                if ((accessFlags & (0x00000400 /*Modifier.ABSTRACT*/ | 0x00000100 /*Modifier.NATIVE*/)) == 0) {
                    buffer.putShort((short) predefinedUtfIndexes[Utils.PS_CODE]);
                    processCodeAttr();
                    attrCount--;
                }

                for (int j = 0; j < attrCount; j++) {
                    processMethodAttr();
                }
            }
        }

        private void processClassAttr() throws IOException {
            int attrCount = readSmallShort3(defDataIn);
            buffer.putShort((short) attrCount);

            if (hasSourceFileAttr) {
                buffer.putShort((short) (sourceFileIndex + firstUtfIndex));
                buffer.putInt(2);
                buffer.putShort((short) (sourceFileIndex + 1 + firstUtfIndex));

                attrCount--;
            }

            for (int j = 0; j < attrCount; j++) {
                processAttr();
            }
        }

        private void processCodeAttr() throws IOException {
            int lengthPosition = buffer.position();

            defDataIn.readFully(buffer.array(), lengthPosition + 4, 4); // read max_stack & max_locals

            buffer.position(lengthPosition + 4 + 4);

            int codeLength = defDataIn.readInt();
            buffer.putInt(codeLength);
            Utils.read(defDataIn, buffer, codeLength);

            patchCode(buffer.position() - codeLength, buffer.position());

            int exceptionTableLength = defDataIn.readUnsignedShort();
            buffer.putShort((short) exceptionTableLength);
            Utils.read(defDataIn, buffer, exceptionTableLength * 4*2);

            int attrCount = readSmallShort3(defDataIn);
            buffer.putShort((short) attrCount);
            for (int i = 0; i < attrCount; i++) {
                processAttr();
            }

            buffer.putInt(lengthPosition, buffer.position() - lengthPosition - 4);
        }

        private void processAttr() throws IOException {
            int nameIndex = readUtfIndexDef();
            buffer.putShort((short) nameIndex);

            processAttrBodyDefault(nameIndex);
        }

        private void processAttrBodyDefault(int nameIndex) throws IOException {
            if (nameIndex == predefinedUtfIndexes[Utils.PS_SIGNATURE]) {
                int signUtf = readUtfIndexDef();
                buffer.putInt(2);
                buffer.putShort((short) signUtf);
                return;
            }

            int length = defDataIn.readInt();
            buffer.putInt(length);
            Utils.read(defDataIn, buffer, length);
        }


        private void processMethodAttr() throws IOException {
            int nameIndex = readUtfIndexDef();
            buffer.putShort((short) nameIndex);

            if (nameIndex == predefinedUtfIndexes[Utils.PS_EXCEPTIONS]) {
                processExceptionAttr();
            }
            else {
                processAttrBodyDefault(nameIndex);
            }
        }

        private void processExceptionAttr() throws IOException {
            int savedPosition = buffer.position();
            buffer.position(savedPosition + 4 + 2);

            do {
                int classIndex = readLimitedShort(defDataIn, classCount);
                if (classIndex == 0) break;

                buffer.putShort((short) classIndex);
            } while (true);

            buffer.putInt(savedPosition, buffer.position() - savedPosition - 4);
            buffer.putShort(savedPosition + 4, (short) ((buffer.position() - savedPosition - 4 - 2) >>> 1));
        }

        private int readUtfIndexDef() throws IOException {
            return firstUtfIndex + readLimitedShort(defDataIn, utfCount - 1);
        }

        private int readUtfIndexPlain() throws IOException {
            return firstUtfIndex + utfLimiter.read(in);
        }

        private int readLimitedShort(DataInput in, int limit) throws IOException {
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
            else if (limit < 256 * 3) {
                res = readSmallShort3(in);
            }
            else {
                res = in.readUnsignedShort();
            }

            assert res <= limit;

            return res;
        }

        private void patchCode(int start, int end) {
            byte[] array = buffer.array();
            int pos = start;

            while (pos < end) {

                // visits the instruction at this offset
                int opcode = array[pos++] & 0xFF;

                switch (InsnTypes.TYPE[opcode]) {
                    case InsnTypes.NOARG_INSN:
                    case InsnTypes.IMPLVAR_INSN:
                        break;

                    case InsnTypes.VAR_INSN:
                    case InsnTypes.SBYTE_INSN:
                    case InsnTypes.LDC_INSN:
                        pos++;
                        break;

                    case InsnTypes.LABEL_INSN:
                    case InsnTypes.SHORT_INSN:
                    case InsnTypes.LDCW_INSN:
                    case InsnTypes.TYPE_INSN:
                    case InsnTypes.IINC_INSN:
                        pos += 2;
                        break;

                    case InsnTypes.LABELW_INSN:
                        pos += 4;
                        break;

                    case InsnTypes.WIDE_INSN:
                        opcode = array[pos++] & 0xFF;
                        if (opcode == 132 /*Opcodes.IINC*/) {
                            pos += 4;
                        } else {
                            pos += 2;
                        }
                        break;

                    case InsnTypes.TABL_INSN: {
                        pos += (4 - ((pos - start) & 3)) & 3; // skips 0 to 3 padding bytes

                        pos += 4; // default ref

                        int min = buffer.getInt(pos);
                        pos += 4;

                        int max = buffer.getInt(pos);
                        pos += 4;
                        assert min <= max;

                        pos += (max - min + 1)*4;
                        break;
                    }

                    case InsnTypes.LOOK_INSN: {
                        pos += (4 - ((pos - start) & 3)) & 3; // skips 0 to 3 padding bytes

                        pos += 4;

                        int len = buffer.getInt(pos);
                        pos += 4 + 8 * len;
                        break;
                    }

                    case InsnTypes.ITFMETH_INSN:
                    case InsnTypes.FIELDORMETH_INSN:
                        int ref = buffer.getShort(pos) & 0xFFFF;

                        if (opcode == 185 /*Opcodes.INVOKEINTERFACE*/) {
                            int imethIndex = ref + firstImethodIndex;
                            buffer.putShort(pos, (short) imethIndex);

                            pos += 2 + 2;
                        }
                        else {
                            if (opcode == 180 /*Opcodes.GETFIELD*/ || opcode == 181 /*Opcodes.PUTFIELD*/
                                    || opcode == 178 /*Opcodes.GETSTATIC*/ || opcode == 179 /*Opcodes.PUTSTATIC*/) {
                                ref += firstFieldIndex;
                            } else if (opcode == 182/*Opcodes.INVOKEVIRTUAL*/ || opcode == 183/*Opcodes.INVOKESPECIAL*/
                                    || opcode == 184 /*Opcodes.INVOKESTATIC*/) {
                                ref += firstMethodIndex;
                            }
                            else {
                                throw new UnsupportedOperationException(String.valueOf(opcode));
                            }

                            buffer.putShort(pos, (short) ref);
                            pos += 2;
                        }
                        break;

    //                case InsnTypes.INDYMETH_INSN: {
    //                    throw new UnsupportedOperationException(); // todo Implement support of INVOKEDYNAMIC!!!
    //                }

                    case InsnTypes.MANA_INSN:
                        pos += 3;
                        break;

                    default:
                        throw new UnsupportedOperationException(String.valueOf(opcode));
                }
            }

            assert pos == end;
        }
    }
}
