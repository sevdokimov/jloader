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

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String jvmClassName = name.replace('.', '/');
        String classFileName = jvmClassName.concat(".c");

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

        private final InputStream defIn;
        private final DataInputStream defDataIn;

        private BitInputStream in;

        private final String className;

        private int interfacesCount;
        private boolean hasSourceFileAttr;

        private ByteBuffer buffer;

        private int utfCount;
        private LimitNumberReader utfLimiter;
        private int firstUtfIndex;

        private int classCount;

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
            this.defIn = defIn;
            this.defDataIn = new DataInputStream(new BufferedInputStream(defIn));

            this.className = className;
        }

        public byte[] unpack() throws IOException {
            interfacesCount = in.readSmall_0_3_8_16();
            hasSourceFileAttr = in.readBoolean();

            int size = readClassSize();

            ByteBuffer buffer = ByteBuffer.allocate(size);
            this.buffer = buffer;
            byte[] array = buffer.array();

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

                int utfIndex = readUtfIndex(in);
                buffer.putShort((short) utfIndex);
            }

            OpenByteOutputStream utfBufferArray = new OpenByteOutputStream();
            DataOutputStream utfOutput = new DataOutputStream(utfBufferArray);

            // Generated utf
            utfOutput.write(1);
            utfOutput.writeUTF(className);
            int processedUtfCount = 1;

            processedUtfCount = extractPredefinedStrings(utfOutput, processedUtfCount, in);
            if (hasSourceFileAttr) {
                sourceFileIndex = processedUtfCount;

                utfOutput.write(1);
                utfOutput.writeUTF("SourceFile");
                utfOutput.write(1);
                utfOutput.writeUTF(Utils.generateSourceFileName(className));

                processedUtfCount += 2;
            }

            int packedStrCount = utfLimiter.read(in);

            firstAnonymousNameIndex = processedUtfCount;
            for (int i = 1; i <= anonymousClassCount; i++) {
                utfOutput.write(1);
                utfOutput.writeUTF(className + '$' + i);
                processedUtfCount++;
            }

            // Packed utf
            HuffmanInputStream<byte[]> huffmanInputStream = new HuffmanInputStream<byte[]>(in, packedStrHuffmanTree);
            for (int i = 0; i < packedStrCount; i++) {
                utfOutput.write((byte) 1);
                byte[] str = huffmanInputStream.read();
                utfOutput.writeShort((short) str.length);
                utfOutput.write(str);
            }
            processedUtfCount += packedStrCount;

            skipConstTableTail(defDataIn, constCount - 1 - classCount
                            - fieldConstCount - imethodConstCount - methodConstCount
                            - nameAndTypeCount
                            - utfCount);

            int firstNameAndType = firstUtfIndex - nameAndTypeCount;

            for (int i = 0; i < fieldConstCount; i++) {
                buffer.put((byte) 9);
                buffer.putShort((short) readLimitedShort(defDataIn, classCount));
                buffer.putShort((short) (readLimitedShort(defDataIn, nameAndTypeCount) + firstNameAndType));
            }

            for (int i = 0; i < imethodConstCount; i++) {
                buffer.put((byte) 11);
                buffer.putShort((short) readLimitedShort(defDataIn, classCount));
                buffer.putShort((short) (readLimitedShort(defDataIn, nameAndTypeCount) + firstNameAndType));
            }

            for (int i = 0; i < methodConstCount; i++) {
                buffer.put((byte) 10);
                buffer.putShort((short) readLimitedShort(defDataIn, classCount));
                buffer.putShort((short) (readLimitedShort(defDataIn, nameAndTypeCount) + firstNameAndType));
            }

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

            assert !buffer.hasRemaining() : className;

            return array;
        }

        private int readClassSize() throws IOException {
            int size = in.readShort();
            if (size < 0) {
                size = (-size) << 15 | in.readShort();
            }

            return size;
        }

        private int extractPredefinedStrings(DataOutputStream utfOutput, int currentUtfIndex, BitInputStream predefinedStrFlags) throws IOException {
            int predefinedUtfCount = Utils.PREDEFINED_UTF.length;
            predefinedUtfIndexes = new int[predefinedUtfCount];

            for (int i = 0; i < predefinedUtfCount; i++) {
                if (predefinedStrFlags.readBoolean()) {
                    predefinedUtfIndexes[i] = currentUtfIndex + firstUtfIndex;

                    currentUtfIndex++;

                    utfOutput.write(1);
                    utfOutput.writeUTF(Utils.PREDEFINED_UTF[i]);
                }
            }

            return currentUtfIndex;
        }

        private void skipConstTableTail(DataInputStream in, int count) throws IOException {
            ByteBuffer buffer = this.buffer;
            byte[] array = buffer.array();

            for (int i = 0; i < count; i++) {
                int tag = in.read();
                buffer.put((byte) tag);

                switch (tag) {
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
            int interfaceCount = interfacesCount;
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
                int accessFlags = defDataIn.readShort();
                buffer.putShort((short) accessFlags);

                int nameIndex = readUtfIndex(defDataIn);
                buffer.putShort((short) nameIndex);

                int descrIndex = readUtfIndex(defDataIn);
                buffer.putShort((short) descrIndex);

                int attrCount = readSmallShort3(defDataIn);
                buffer.putShort((short) attrCount);

                if ((accessFlags & (0x00000400 /*Modifier.ABSTRACT*/ | 0x00000100 /*Modifier.NATIVE*/)) == 0) {
                    buffer.putShort((short) predefinedUtfIndexes[Utils.PS_CODE]);
                    processCodeAttr(defDataIn);
                    attrCount--;
                }

                for (int j = 0; j < attrCount; j++) {
                    processMethodAttr(defDataIn);
                }
            }
        }

        private void processClassAttr(DataInputStream defDataIn) throws IOException {
            int attrCount = readSmallShort3(defDataIn);
            buffer.putShort((short) attrCount);

            if (hasSourceFileAttr) {
                buffer.putShort((short) (sourceFileIndex + firstUtfIndex));
                buffer.putInt(2);
                buffer.putShort((short) (sourceFileIndex + 1 + firstUtfIndex));

                attrCount--;
            }

            for (int j = 0; j < attrCount; j++) {
                processAttr(defDataIn);
            }
        }

        private void processCodeAttr(DataInputStream defDataIn) throws IOException {
            int lengthPosition = buffer.position();

            defDataIn.readFully(buffer.array(), lengthPosition + 4, 4); // read max_stack & max_locals

            buffer.position(lengthPosition + 4 + 4);

            int codeLength = defDataIn.readInt();
            buffer.putInt(codeLength);
            Utils.read(defDataIn, buffer, codeLength);

            patchCode(ByteBuffer.wrap(buffer.array(), buffer.position() - codeLength, codeLength));

            int exceptionTableLength = defDataIn.readUnsignedShort();
            buffer.putShort((short) exceptionTableLength);
            Utils.read(defDataIn, buffer, exceptionTableLength * 4*2);

            int attrCount = readSmallShort3(defDataIn);
            buffer.putShort((short) attrCount);
            for (int i = 0; i < attrCount; i++) {
                processAttr(defDataIn);
            }

            buffer.putInt(lengthPosition, buffer.position() - lengthPosition - 4);
        }

        private void processAttr(DataInputStream defDataIn) throws IOException {
            int nameIndex = readUtfIndex(defDataIn);
            buffer.putShort((short) nameIndex);

            processAttrBodyDefault(defDataIn, nameIndex);
        }

        private void processAttrBodyDefault(DataInputStream defDataIn, int nameIndex) throws IOException {
            if (nameIndex == predefinedUtfIndexes[Utils.PS_SIGNATURE]) {
                int signUtf = readUtfIndex(defDataIn);
                buffer.putInt(2);
                buffer.putShort((short) signUtf);
                return;
            }

            int length = defDataIn.readInt();
            buffer.putInt(length);
            Utils.read(defDataIn, buffer, length);
        }


        private void processMethodAttr(DataInputStream defDataIn) throws IOException {
            int nameIndex = readUtfIndex(defDataIn);
            buffer.putShort((short) nameIndex);

            if (nameIndex == predefinedUtfIndexes[Utils.PS_EXCEPTIONS]) {
                processExceptionAttr(defDataIn);
            }
            else {
                processAttrBodyDefault(defDataIn, nameIndex);
            }
        }

        private void processExceptionAttr(DataInputStream defDataIn) throws IOException {
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

        private int readUtfIndex(DataInput in) throws IOException {
            return firstUtfIndex + readLimitedShort(in, utfCount - 1);
        }

        private int readUtfIndex(BitInputStream in) throws IOException {
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

        private void skipPadding(ByteBuffer codeBuffer, int startPosition) {
            // skips 0 to 3 padding bytes
            while (((codeBuffer.position() - startPosition) & 3) > 0) {
                if (codeBuffer.get() != 0) {
                    throw new RuntimeException();
                }
            }
        }

        private void patchCode(ByteBuffer codeBuffer) {
            int startPosition = codeBuffer.position();

            while (codeBuffer.hasRemaining()) {

                // visits the instruction at this offset
                int opcode = codeBuffer.get() & 0xFF;

                switch (InsnTypes.TYPE[opcode]) {
                    case InsnTypes.NOARG_INSN:
                    case InsnTypes.IMPLVAR_INSN:
                        break;

                    case InsnTypes.VAR_INSN:
                    case InsnTypes.SBYTE_INSN:
                    case InsnTypes.LDC_INSN:
                        codeBuffer.get();
                        break;

                    case InsnTypes.LABEL_INSN:
                    case InsnTypes.SHORT_INSN:
                    case InsnTypes.LDCW_INSN:
                    case InsnTypes.TYPE_INSN:
                    case InsnTypes.IINC_INSN:
                        codeBuffer.getShort();
                        break;

                    case InsnTypes.LABELW_INSN:
                        codeBuffer.getInt();
                        break;

                    case InsnTypes.WIDE_INSN:
                        opcode = codeBuffer.get() & 0xFF;
                        if (opcode == 132 /*Opcodes.IINC*/) {
                            codeBuffer.getInt();
                        } else {
                            codeBuffer.getShort();
                        }
                        break;

                    case InsnTypes.TABL_INSN: {
                        skipPadding(codeBuffer, startPosition);

                        codeBuffer.getInt(); // default ref

                        int min = codeBuffer.getInt();
                        int max = codeBuffer.getInt();
                        assert min <= max;

                        codeBuffer.position(codeBuffer.position() + (max - min + 1)*4);

                        break;
                    }

                    case InsnTypes.LOOK_INSN: {
                        skipPadding(codeBuffer, startPosition);

                        int defaultLabel = codeBuffer.getInt();
                        assert defaultLabel >= 0 && defaultLabel < codeBuffer.limit();

                        int len = codeBuffer.getInt();
                        codeBuffer.position(codeBuffer.position() + 8 * len);
                        break;
                    }

                    case InsnTypes.ITFMETH_INSN:
                    case InsnTypes.FIELDORMETH_INSN:
                        if (opcode == 180 /*Opcodes.GETFIELD*/ || opcode == 181 /*Opcodes.PUTFIELD*/
                                || opcode == 178 /*Opcodes.GETSTATIC*/ || opcode == 179 /*Opcodes.PUTSTATIC*/) {
                            int fieldIndex = (codeBuffer.getShort() & 0xFFFF) + firstFieldIndex;
                            codeBuffer.putShort(codeBuffer.position() - 2, (short) fieldIndex);
                        } else if (opcode == 185 /*Opcodes.INVOKEINTERFACE*/) {
                            int imethIndex = (codeBuffer.getShort() & 0xFFFF) + firstImethodIndex;
                            codeBuffer.putShort(codeBuffer.position() - 2, (short) imethIndex);

                            codeBuffer.getShort();
                        }
                        else if (opcode == 182/*Opcodes.INVOKEVIRTUAL*/ || opcode == 183/*Opcodes.INVOKESPECIAL*/
                                || opcode == 184 /*Opcodes.INVOKESTATIC*/) {
                            int methIndex = (codeBuffer.getShort() & 0xFFFF) + firstMethodIndex;
                            codeBuffer.putShort(codeBuffer.position() - 2, (short) methIndex);
                        }
                        else {
                            throw new UnsupportedOperationException(String.valueOf(opcode));
                        }
                        break;

    //                case InsnTypes.INDYMETH_INSN: {
    //                    throw new UnsupportedOperationException(); // todo Implement support of INVOKEDYNAMIC!!!
    //                }

                    case InsnTypes.MANA_INSN:
                        codeBuffer.getShort();
                        codeBuffer.get();
                        break;

                    default:
                        throw new UnsupportedOperationException(String.valueOf(opcode));
                }
            }

        }
    }
}
