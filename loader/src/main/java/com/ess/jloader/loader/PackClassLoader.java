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

    public static final String METADATA_ENTRY_NAME = "META-INF/literals.data";

    private final HuffmanUtils.TreeElement packedStrHuffmanTree;

    private final int[] versions = new int[8];

    private final byte[] dictionary;

    private final URLClassLoader delegateClassLoader;

    private final ZipFile zipFile;

    public PackClassLoader(ClassLoader parent, File packFile) throws IOException {
        super(parent);

        delegateClassLoader = new URLClassLoader(new URL[]{packFile.toURI().toURL()}, null);

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
                int count = Utils.readSmallShort3(inputStream);
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

                BitInputStream in = new BitInputStream(plainData, 0, plainSize);

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

    private class Unpacker {

        private final DataInputStream defDataIn;

        private final BitInputStream in;

        private final String className;

        private boolean hasSourceFileAttr;

        private ByteBuffer buffer;

        private ConstIndexInterval classesInterval;
        private ConstIndexInterval fieldInterval;
        private ConstIndexInterval imethodInterval;
        private ConstIndexInterval methodInterval;
        private ConstIndexInterval nameAndTypeInterval;
        private ConstIndexInterval utfInterval;

        private int[] predefinedUtfIndexes;

        private int sourceFileIndex;

        private int anonymousClassCount;
        private int firstAnonymousNameIndex;

        private int generatedStrIndex;
        private DataOutputStream generatedStrDataOutput;

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

            // Magic
            buffer.putInt(0xCAFEBABE);

            // Version
            buffer.putInt(versions[in.readBits(3)]);

            anonymousClassCount = in.readSmall_0_3_8_16();

            // Const count
            int constCount = Utils.readSmallShort3(in);
            buffer.putShort((short) constCount);

            utfInterval = ConstIndexInterval.create(constCount, in.readLimitedShort(constCount));
            nameAndTypeInterval = new ConstIndexInterval(utfInterval, Utils.readSmallShort3(in));
            methodInterval = new ConstIndexInterval(nameAndTypeInterval, Utils.readSmallShort3(in));
            imethodInterval = new ConstIndexInterval(methodInterval, Utils.readSmallShort3(in));
            fieldInterval = new ConstIndexInterval(imethodInterval, Utils.readSmallShort3(in));

            classesInterval = new ConstIndexInterval(1, in.readLimitedShort(utfInterval.getCount()));

            buffer.put((byte) 7);
            buffer.putShort((short) utfInterval.getFirstIndex()); // Class name;

            for (int i = 1; i < classesInterval.getCount(); i++) {
                buffer.put((byte) 7);

                int utfIndex = utfInterval.readIndexCompact(in);
                buffer.putShort((short) utfIndex);
            }

            skipConstTableTail(constCount - 1 - classesInterval.getCount()
                            - fieldInterval.getCount() - imethodInterval.getCount() - methodInterval.getCount()
                            - nameAndTypeInterval.getCount()
                            - utfInterval.getCount());

            for (int i = fieldInterval.getCount(); --i >= 0; ) {
                buffer.put((byte) 9);
                buffer.putShort((short) (classesInterval.readIndexCompact(in)));
                buffer.putShort((short) (nameAndTypeInterval.readIndexCompact(in)));
            }

            for (int i = imethodInterval.getCount(); --i >= 0; ) {
                buffer.put((byte) 11);
                buffer.putShort((short) (classesInterval.readIndexCompact(in)));
                buffer.putShort((short) (nameAndTypeInterval.readIndexCompact(in)));
            }

            for (int i = methodInterval.getCount(); --i >= 0; ) {
                buffer.put((byte) 10);
                buffer.putShort((short) (classesInterval.readIndexCompact(in)));
                buffer.putShort((short) (nameAndTypeInterval.readIndexCompact(in)));
            }

            for (int i = nameAndTypeInterval.getCount(); --i >= 0; ) {
                buffer.put((byte) 12); // ClassWriter.NAME_TYPE
                buffer.putShort((short) utfInterval.readIndexCompact(in));
                buffer.putShort((short) utfInterval.readIndexCompact(in));
            }

            generatedStrIndex = utfInterval.getFirstIndex();
            generatedStrDataOutput = new DataOutputStream(new OpenByteOutputStream(array, buffer.position()));

            int generatedStrSize = Utils.readSmallShort3(in);

            buffer.position(buffer.position() + generatedStrSize);

            // Generated utf
            putGeneratedStr(className);

            extractPredefinedStrings();

            if (hasSourceFileAttr) {
                sourceFileIndex = putGeneratedStr(Utils.C_SourceFile);
                putGeneratedStr(Utils.generateSourceFileName(className));
            }

            firstAnonymousNameIndex = generatedStrIndex;
            for (int i = 1; i <= anonymousClassCount; i++) {
                putGeneratedStr(className + '$' + i);
            }

            // Packed utf
            int packedStrCount = in.readLimitedShort(utfInterval.getCount());

            HuffmanInputStream<byte[]> huffmanInputStream = new HuffmanInputStream<byte[]>(in, packedStrHuffmanTree);
            for (int i = 0; i < packedStrCount; i++) {
                buffer.put((byte) 1);
                byte[] str = huffmanInputStream.read();
                buffer.putShort((short) str.length);
                buffer.put(str);
            }

            int notPackedStrCount = in.readLimitedShort(utfInterval.getCount());
            for (int i = 0; i < notPackedStrCount; i++) {
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

            assert generatedStrDataOutput.size() == generatedStrSize;
            assert generatedStrIndex == constCount - packedStrCount - notPackedStrCount;

            return array;
        }

        private int putGeneratedStr(String s) throws IOException {
            generatedStrDataOutput.write(1);
            generatedStrDataOutput.writeUTF(s);

            return generatedStrIndex++;
        }

        private int putGeneratedStr(byte[] bytes) throws IOException {
            return putGeneratedStr(bytes, 0, bytes.length);
        }

        private int putGeneratedStr(byte[] bytes, int offset, int len) throws IOException {
            generatedStrDataOutput.write(1);
            generatedStrDataOutput.write(bytes, offset, len);

            return generatedStrIndex++;
        }

        private int readClassSize() throws IOException {
            int size = in.readShort();
            if (size < 0) {
                size = (-size) << 15 | in.readShort();
            }

            return size;
        }

        private void extractPredefinedStrings() throws IOException {
            int predefinedUtfCount = Utils.PREDEFINED_UTF.length;
            predefinedUtfIndexes = new int[predefinedUtfCount];

            for (int i = 0; i < predefinedUtfCount; i++) {
                if (in.readBoolean()) {
                    int utfStart = Utils.PREDEFINED_UTF_BYTE_INDEXES[i];
                    int utfLen = Utils.PREDEFINED_UTF_BYTE_INDEXES[i + 1] - utfStart;
                    predefinedUtfIndexes[i] = putGeneratedStr(Utils.PREDEFINED_UTF_BYTES, utfStart, utfLen);
                }
            }
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
                        buffer.putShort((short) (utfInterval.readIndexCompact(in)));
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
                int classIndex = classesInterval.readIndexCompact(in);
                buffer.putShort((short) classIndex);
            }
        }

        private void processFields() throws IOException {
            int fieldCount = Utils.readSmallShort3(defDataIn);
            buffer.putShort((short) fieldCount);

            for (int i = 0; i < fieldCount; i++) {
                short accessFlags = defDataIn.readShort();
                buffer.putShort(accessFlags);

                int nameIndex = utfInterval.readIndexCompact(in);
                buffer.putShort((short) nameIndex);

                int descrIndex = utfInterval.readIndexCompact(in);
                buffer.putShort((short) descrIndex);

                int attrCount = Utils.readSmallShort3(defDataIn);
                buffer.putShort((short) attrCount);

                for (int j = 0; j < attrCount; j++) {
                    processAttr();
                }
            }
        }

        private void processMethods() throws IOException {
            int methodCount = Utils.readSmallShort3(defDataIn);
            buffer.putShort((short) methodCount);

            for (int i = 0; i < methodCount; i++) {
                int accessFlags = defDataIn.readShort();
                buffer.putShort((short) accessFlags);

                int nameIndex = utfInterval.readIndexCompact(in);
                buffer.putShort((short) nameIndex);

                int descrIndex = utfInterval.readIndexCompact(in);
                buffer.putShort((short) descrIndex);

                int attrCount = Utils.readSmallShort3(defDataIn);
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
            int attrCount = Utils.readSmallShort3(defDataIn);
            buffer.putShort((short) attrCount);

            if (hasSourceFileAttr) {
                buffer.putShort((short) (sourceFileIndex));
                buffer.putInt(2);
                buffer.putShort((short) (sourceFileIndex + 1));

                attrCount--;
            }

            for (int j = 0; j < attrCount; j++) {
                processAttr();
            }
        }

        private void processCodeAttr() throws IOException {
            int lengthPosition = buffer.position();
            buffer.position(lengthPosition + 4);

            int maxStack = Utils.readSmallShort3(defDataIn);
            buffer.putShort((short) maxStack);

            int maxLocals = Utils.readSmallShort3(defDataIn);
            buffer.putShort((short) maxLocals);

            buffer.position(buffer.position() + 4); // this is a place to store code length

            readCode();

            int codeLength = buffer.position() - lengthPosition - 4 - 2 - 2 - 4;
            buffer.putInt(lengthPosition + 4 + 2 + 2, codeLength);

            int exceptionTableLength = Utils.readSmallShort3(defDataIn);
            buffer.putShort((short) exceptionTableLength);
            Utils.read(defDataIn, buffer, exceptionTableLength * 4*2);

            int attrCount = Utils.readSmallShort3(defDataIn);
            buffer.putShort((short) attrCount);
            for (int i = 0; i < attrCount; i++) {
                processAttr();
            }

            buffer.putInt(lengthPosition, buffer.position() - lengthPosition - 4);
        }

        private void processAttr() throws IOException {
            int nameIndex = utfInterval.readIndexCompact(defDataIn);
            buffer.putShort((short) nameIndex);

            processAttrBodyDefault(nameIndex);
        }

        private void processAttrBodyDefault(int nameIndex) throws IOException {
            if (nameIndex == predefinedUtfIndexes[Utils.PS_SIGNATURE]) {
                int signUtf = utfInterval.readIndexCompact(defDataIn);
                buffer.putInt(2);
                buffer.putShort((short) signUtf);
                return;
            }

            int length = defDataIn.readInt();
            buffer.putInt(length);
            Utils.read(defDataIn, buffer, length);
        }


        private void processMethodAttr() throws IOException {
            int nameIndex = utfInterval.readIndexCompact(defDataIn);
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
                int classIndex = Utils.readLimitedShort(defDataIn, classesInterval.getCount());
                if (classIndex == 0) break;

                buffer.putShort((short) classIndex);
            } while (true);

            buffer.putInt(savedPosition, buffer.position() - savedPosition - 4);
            buffer.putShort(savedPosition + 4, (short) ((buffer.position() - savedPosition - 4 - 2) >>> 1));
        }

        private void readCode() throws IOException {
            byte[] array = buffer.array();
            int pos = buffer.position();

            loop:
            while (true) {

                // visits the instruction at this offset
                int opcode = defDataIn.read();
                array[pos++] = (byte) opcode;

                switch (InsnTypes.TYPE[opcode]) {
                    case InsnTypes.NOARG_INSN:
                    case InsnTypes.IMPLVAR_INSN:
                        break;

                    case InsnTypes.VAR_INSN:
                    case InsnTypes.SBYTE_INSN:
                    case InsnTypes.LDC_INSN:
                        array[pos++] = (byte) defDataIn.read();
                        break;

                    case InsnTypes.LABEL_INSN:
                    case InsnTypes.SHORT_INSN:
                    case InsnTypes.LDCW_INSN:
                    case InsnTypes.TYPE_INSN:
                    case InsnTypes.IINC_INSN:
                        defDataIn.readFully(array, pos, 2);
                        pos += 2;
                        break;

                    case InsnTypes.LABELW_INSN:
                        defDataIn.readFully(array, pos, 4);
                        pos += 4;
                        break;

                    case InsnTypes.WIDE_INSN:
                        opcode = defDataIn.read();
                        array[pos++] = (byte) opcode;

                        int len;
                        if (opcode == 132 /*Opcodes.IINC*/) {
                            len = 4;
                        } else {
                            len = 2;
                        }
                        pos += len;

                        defDataIn.readFully(array, pos, len);
                        break;

                    case InsnTypes.TABL_INSN: {
                        pos += (4 - ((pos - buffer.position()) & 3)) & 3; // skips 0 to 3 padding bytes

                        defDataIn.readFully(array, pos, 4); // default ref
                        pos += 4;

                        int min = defDataIn.readInt();
                        buffer.putInt(pos, min);
                        pos += 4;

                        int max = defDataIn.readInt();
                        buffer.putInt(pos, max);
                        pos += 4;
                        assert min <= max;

                        len = (max - min + 1)*4;
                        defDataIn.readFully(array, pos, len);
                        pos += len;
                        break;
                    }

                    case InsnTypes.LOOK_INSN: {
                        pos += (4 - ((pos - buffer.position()) & 3)) & 3; // skips 0 to 3 padding bytes

                        defDataIn.readFully(array, pos, 4); // default ref
                        pos += 4;

                        len = defDataIn.readInt();
                        buffer.putInt(pos, len);
                        pos += 4;

                        defDataIn.readFully(array, pos, 8 * len);

                        pos += 8 * len;
                        break;
                    }

                    case InsnTypes.ITFMETH_INSN:
                    case InsnTypes.FIELDORMETH_INSN:
                        int ref = defDataIn.readUnsignedShort();

                        if (opcode == 185 /*Opcodes.INVOKEINTERFACE*/) {
                            int imethIndex = ref + imethodInterval.getFirstIndex();
                            buffer.putShort(pos, (short) imethIndex);
                            pos += 2;

                            array[pos++] = (byte) defDataIn.read();
                            pos++; // put 0
                        }
                        else {
                            if (opcode == 180 /*Opcodes.GETFIELD*/ || opcode == 181 /*Opcodes.PUTFIELD*/
                                    || opcode == 178 /*Opcodes.GETSTATIC*/ || opcode == 179 /*Opcodes.PUTSTATIC*/) {
                                ref += fieldInterval.getFirstIndex();
                            } else if (opcode == 182/*Opcodes.INVOKEVIRTUAL*/ || opcode == 183/*Opcodes.INVOKESPECIAL*/
                                    || opcode == 184 /*Opcodes.INVOKESTATIC*/) {
                                ref += methodInterval.getFirstIndex();
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
                        defDataIn.readFully(array, pos, 3);
                        pos += 3;
                        break;

                    case InsnTypes.UNKNOWN_INSN:
                        if (opcode == 255) {
                            pos--; // remove end of code marker
                            break loop;
                        }

                        throw new UnsupportedOperationException(String.valueOf(opcode));

                    default:
                        throw new UnsupportedOperationException(String.valueOf(opcode));
                }
            }

            buffer.position(pos);
        }
    }
}
