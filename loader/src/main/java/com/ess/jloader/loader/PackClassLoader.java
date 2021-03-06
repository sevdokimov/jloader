package com.ess.jloader.loader;

import com.ess.jloader.utils.*;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.PriorityQueue;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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

            if (packedStringsCount > 0) {
                byte[][] packedStrings = new byte[packedStringsCount][];

                for (int i = 0; i < packedStringsCount; i++) {
                    int len = inputStream.readUnsignedShort();
                    packedStrings[i] = new byte[len];
                    inputStream.readFully(packedStrings[i]);
                }

                // Build Huffman tree
                PriorityQueue<HuffmanUtils.TreeElement> queue = new PriorityQueue<HuffmanUtils.TreeElement>(packedStringsCount);
                for (int i = 0; i < packedStringsCount; i++) {
                    int count = Utils.readSmallShort3(inputStream);
                    queue.add(new HuffmanUtils.Leaf(count, packedStrings[i]));
                }
                packedStrHuffmanTree = HuffmanUtils.buildHuffmanTree(queue);
            }
            else {
                packedStrHuffmanTree = null;
            }

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

    public byte[] unpackClass(String jvmClassName) throws IOException {
        String classFileName = jvmClassName.concat(".class");

        ZipEntry entry = zipFile.getEntry(classFileName);
        if (entry == null) return null;

        InputStream inputStream = zipFile.getInputStream(entry);

        try {
            DataInputStream dataInputStream = new DataInputStream(inputStream);
            int plainSize = Utils.readShortInt(dataInputStream);
            byte[] plainData = new byte[plainSize];
            dataInputStream.readFully(plainData);

            BitInputStream in = new BitInputStream(plainData, 0, plainSize);

            return unpackClass(in, new BufferedInputStream(inputStream), jvmClassName);
        } finally {
            inputStream.close();
        }
    }

    public byte[] unpackClass(BitInputStream in, InputStream dataIn, String jvmClassName) throws IOException {
        Inflater inflater = new Inflater(true);
        inflater.setDictionary(dictionary);
        InflaterInputStream defIn = new InflaterInputStream(dataIn, inflater);

        try {
            Unpacker unpacker = new Unpacker(in, defIn, jvmClassName);

            return unpacker.unpack();
        } finally {
            inflater.end();
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String jvmClassName = name.replace('.', '/');

        byte[] classData;

        try {
            classData = unpackClass(jvmClassName);
            if (classData == null) throw new ClassNotFoundException(name);
        } catch (IOException e) {
            throw new ClassNotFoundException(name);
        }

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

        private FastBuffer buffer;

        private ConstIndexInterval classesInterval;
        private ConstIndexInterval fieldInterval;
        private ConstIndexInterval imethodInterval;
        private ConstIndexInterval methodInterval;
        private ConstIndexInterval nameAndTypeInterval;
        private ConstIndexInterval utfInterval;

        private int[] predefinedUtfIndexes = new int[Utils.PREDEFINED_UTF.length];

        private int generatedStrIndex;
        private DataOutputStream generatedStrDataOutput;

        private int maxLineNumberBits;

        private int classTypeIndex;

        public Unpacker(BitInputStream in, InputStream defIn, String className) {
            this.in = in;
            this.defDataIn = new DataInputStream(new BufferedInputStream(defIn));

            this.className = className;
        }

        public byte[] unpack() throws IOException {
            FastBuffer buffer = new FastBuffer(Utils.readShortInt(in));
            this.buffer = buffer;

            // Magic
            buffer.putInt(0xCAFEBABE);

            // Version
            buffer.putInt(versions[in.readBits(3)]);

            // Const count
            int constCount = Utils.readSmallShort3(in);
            buffer.putShort(constCount);

            utfInterval = ConstIndexInterval.create(constCount, in.readLimitedShort(constCount));
            nameAndTypeInterval = new ConstIndexInterval(utfInterval, Utils.readSmallShort3(in));
            methodInterval = new ConstIndexInterval(nameAndTypeInterval, Utils.readSmallShort3(in));
            imethodInterval = new ConstIndexInterval(methodInterval, Utils.readSmallShort3(in));
            fieldInterval = new ConstIndexInterval(imethodInterval, Utils.readSmallShort3(in));

            classesInterval = new ConstIndexInterval(1, in.readLimitedShort(utfInterval.count));

            buffer.put((byte) 7);
            buffer.putShort(utfInterval.firstIndex); // Class name;

            for (int i = 1; i < classesInterval.count; i++) {
                buffer.put((byte) 7);

                int utfIndex = utfInterval.readIndexCompact(in);
                buffer.putShort(utfIndex);
            }

            skipConstTableTail(constCount - 1 - classesInterval.count
                            - fieldInterval.count - imethodInterval.count - methodInterval.count
                            - nameAndTypeInterval.count
                            - utfInterval.count);

            for (int i = fieldInterval.count; --i >= 0; ) {
                buffer.put((byte) 9);
                buffer.putShort((classesInterval.readIndexCompact(in)));
                buffer.putShort(nameAndTypeInterval.readIndexCompact(in));
            }

            for (int i = imethodInterval.count; --i >= 0; ) {
                buffer.put((byte) 11);
                buffer.putShort(classesInterval.readIndexCompact(in));
                buffer.putShort(nameAndTypeInterval.readIndexCompact(in));
            }

            for (int i = methodInterval.count; --i >= 0; ) {
                buffer.put((byte) 10);
                buffer.putShort(classesInterval.readIndexCompact(in));
                buffer.putShort(nameAndTypeInterval.readIndexCompact(in));
            }

            for (int i = nameAndTypeInterval.count; --i >= 0; ) {
                buffer.put((byte) 12); // ClassWriter.NAME_TYPE
                buffer.putShort(utfInterval.readIndexCompact(in));
                buffer.putShort(utfInterval.readIndexCompact(in));
            }

            generatedStrIndex = utfInterval.firstIndex;
            generatedStrDataOutput = new DataOutputStream(new OpenByteOutputStream(buffer.array, buffer.pos));

            int generatedStrSize = Utils.readSmallShort3(in);

            buffer.skip(generatedStrSize);

            // Generated utf
            putGeneratedStr(className);

            extractCommonUtf();

            // Packed utf
            int packedStrCount = in.readLimitedShort(utfInterval.count);

            HuffmanInputStream<byte[]> huffmanInputStream = new HuffmanInputStream<byte[]>(in, packedStrHuffmanTree);
            for (int i = 0; i < packedStrCount; i++) {
                buffer.put((byte) 1);
                byte[] str = huffmanInputStream.read();
                buffer.putShort(str.length);
                buffer.put(str);
            }

            int notPackedStrCount = in.readLimitedShort(utfInterval.count);
            for (int i = 0; i < notPackedStrCount; i++) {
                buffer.put((byte) 1);
                int utfSize = defDataIn.readUnsignedShort();
                buffer.putShort(utfSize);
                buffer.readFully(defDataIn, utfSize);
            }

            int accessFlags = defDataIn.readShort();
            buffer.putShort(accessFlags);

            buffer.putShort(1); // this class name index
            buffer.putShort(2); // super class name index

            // Process interfaces
            processInterfaces();

            processFields();
            processMethods();
            processClassAttr();

            assert generatedStrIndex == constCount - packedStrCount - notPackedStrCount : className;
            assert generatedStrDataOutput.size() == generatedStrSize : className;

            assert buffer.pos == buffer.array.length : className;

            return buffer.array;
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

        // See Utils.PREDEFINED_UTF
        private int putPredefinedGeneratedString(int predefinedStrId) throws IOException {
            int res = predefinedUtfIndexes[predefinedStrId];
            if (res == 0) {
                int strStart = Utils.PREDEFINED_UTF_BYTE_INDEXES[predefinedStrId];
                res = putGeneratedStr(Utils.PREDEFINED_UTF_BYTES, strStart, Utils.PREDEFINED_UTF_BYTE_INDEXES[predefinedStrId + 1] - strStart);
                predefinedUtfIndexes[predefinedStrId] = res;
            }

            return res;
        }

        private void extractCommonUtf() throws IOException {
            for (String s : Utils.COMMON_UTF) {
                if (in.readBoolean()) {
                    putGeneratedStr(s);
                }
            }
        }

        private void skipConstTableTail(int count) throws IOException {
            FastBuffer buffer = this.buffer;

            for (int i = 0; i < count; i++) {
                int tag = defDataIn.read();
                buffer.put((byte) tag);

                switch (tag) {
                    case 3: // ClassWriter.INT:
                    case 4: // ClassWriter.FLOAT:
                    case 18: // ClassWriter.INDY:
                        buffer.readFully(defDataIn, 4);
                        break;

                    case 5:// ClassWriter.LONG:
                    case 6: // ClassWriter.DOUBLE:
                        buffer.readFully(defDataIn, 8);
                        ++i;
                        break;

                    case 15: // ClassWriter.HANDLE:
                        if (true) throw new UnsupportedOperationException();
                        buffer.readFully(defDataIn, 3);
                        break;

                    case 8: // ClassWriter.STR
                    case 16: // ClassWriter.MTYPE
                        buffer.putShort(utfInterval.readIndexCompact(in));
                        break;

                    default:
                        throw new RuntimeException();
                }
            }
        }

        private void processInterfaces() throws IOException {
            int interfaceCount = in.readSmall_0_3_8_16();
            buffer.putShort(interfaceCount);

            for (int i = 0; i < interfaceCount; i++) {
                int classIndex = classesInterval.readIndexCompact(in);
                buffer.putShort(classIndex);
            }
        }

        private void processFields() throws IOException {
            int fieldCount = Utils.readSmallShort3(defDataIn);
            buffer.putShort(fieldCount);

            for (int i = 0; i < fieldCount; i++) {
                int accessFlags = defDataIn.readUnsignedShort();
                buffer.putShort(accessFlags);

                int nameIndex = utfInterval.readIndexCompact(in);
                buffer.putShort(nameIndex);

                int descrIndex = utfInterval.readIndexCompact(in);
                buffer.putShort(descrIndex);

                // Process attributes
                int attrInfo = Utils.readSmallShort3(defDataIn);
                int attrCountPosition = buffer.pos;
                int processedAttrCount = 0;
                buffer.skip(2); // the place to store attr count

                if ((attrInfo & 1) > 0) {
                    processSignatureAttr();
                    processedAttrCount++;
                }

                if ((attrInfo & 2) > 0) {
                    processConstantValueAttr();
                    processedAttrCount++;
                }

                int unknownAttrCount = attrInfo >>> 2;

                for (int j = 0; j < unknownAttrCount; j++) {
                    processAttr();
                }

                buffer.putShort(attrCountPosition, unknownAttrCount + processedAttrCount);
            }
        }

        private void processMethods() throws IOException {
            int methodCount = Utils.readSmallShort3(defDataIn);
            buffer.putShort(methodCount);

            for (int i = 0; i < methodCount; i++) {
                int accessFlags = defDataIn.readShort();
                buffer.putShort(accessFlags);

                int nameIndex = utfInterval.readIndexCompact(in);
                buffer.putShort(nameIndex);

                int descrIndex = utfInterval.readIndexCompact(in);
                buffer.putShort(descrIndex);

                int attrInfo = Utils.readSmallShort3(defDataIn);
                int attrCountPosition = buffer.pos;
                int processedAttrCount = 0;
                buffer.skip(2); // the place to store attr count

                if ((accessFlags & (0x00000400 /*Modifier.ABSTRACT*/ | 0x00000100 /*Modifier.NATIVE*/)) == 0) {
                    processCodeAttr();
                    processedAttrCount++;
                }

                if ((attrInfo & 1) > 0) {
                    processSignatureAttr();
                    processedAttrCount++;
                }

                if ((attrInfo & 2) > 0) {
                    processExceptionAttr();
                    processedAttrCount++;
                }

                int unknownAttrCount = attrInfo >>> 2;

                for (int j = 0; j < unknownAttrCount; j++) {
                    processAttr();
                }

                buffer.putShort(attrCountPosition, unknownAttrCount + processedAttrCount);
            }
        }

        private void processClassAttr() throws IOException {
            int attrInfo = Utils.readSmallShort3(defDataIn);

            int attrCountPosition = buffer.pos;
            int processedAttrCount = 0;
            buffer.skip(2); // the place to store attr count

            if ((attrInfo & 1) > 0) {
                buffer.putShort(putGeneratedStr(Utils.C_SourceFile));
                buffer.putInt(2);

                if (in.readBoolean()) {
                    buffer.putShort(putGeneratedStr(Utils.generateSourceFileName(className)));
                }
                else {
                    int utfIndex = utfInterval.readIndexCompact(in);
                    buffer.putShort(utfIndex);
                }

                processedAttrCount++;
            }

            if ((attrInfo & 2) > 0) {
                processInnerClassesAttr();
                processedAttrCount++;
            }

            if ((attrInfo & 4) > 0) {
                processSignatureAttr();
                processedAttrCount++;
            }

            if ((attrInfo & 8) > 0) {
                processEnclosingMethodAttr();
                processedAttrCount++;
            }

            int unknownAttrCount = attrInfo >>> 4;

            for (int j = 0; j < unknownAttrCount; j++) {
                processAttr();
            }

            buffer.putShort(attrCountPosition, unknownAttrCount + processedAttrCount);
        }

        private void processInnerClassesAttr() throws IOException {
            buffer.putShort(putGeneratedStr(Utils.C_InnerClasses));

            int anonymousClassCount = in.readSmall_0_3_8_16();
            for (int i = 1; i <= anonymousClassCount; i++) {
                putGeneratedStr(className + '$' + i);
            }

            int length = in.readLimitedShort(classesInterval.count);
            buffer.putInt(2 + length*4*2);
            buffer.putShort(length);

            for (int i = 0; i < length; i++) {
                int innerClassIndex = classesInterval.readIndexCompact(in);
                buffer.putShort(innerClassIndex);

                int outerClassIndex = classesInterval.readIndexCompactNullable(defDataIn);
                buffer.putShort(outerClassIndex);

                int innerName = utfInterval.readIndexCompactNullable(in);
                buffer.putShort(innerName);

                int access = defDataIn.readUnsignedShort();
                buffer.putShort(access);
            }
        }

        private void processCodeAttr() throws IOException {
            buffer.putShort(putPredefinedGeneratedString(Utils.PS_CODE));

            int lengthPosition = buffer.pos;
            buffer.skip(4);

            int maxStack = Utils.readSmallShort3(defDataIn);
            buffer.putShort(maxStack);

            int maxLocals = Utils.readSmallShort3(defDataIn);
            buffer.putShort(maxLocals);

            buffer.skip(4); // this is a place to store code length

            readCode();

            int codeLength = buffer.pos - lengthPosition - 4 - 2 - 2 - 4;
            buffer.putInt(lengthPosition + 4 + 2 + 2, codeLength);

            int exceptionTableLength = Utils.readSmallShort3(defDataIn);
            buffer.putShort(exceptionTableLength);
            buffer.readFully(defDataIn, exceptionTableLength * 4 * 2);

            // Process attributes
            int attrInfo = Utils.readSmallShort3(defDataIn);
            int attrCountPosition = buffer.pos;
            buffer.skip(2); // the place to store attr count

            if ((attrInfo & 1) > 0) {
                processLineNumbersAttr(codeLength);
            }

            int localVarAttrInfo = attrInfo & (2|4);
            if (localVarAttrInfo > 0) {
                processLocalVarTableAttr(codeLength, maxLocals, localVarAttrInfo > 2);
            }

            if ((attrInfo & 8) > 0) {
                processStackMapAttr();
            }

            int unknownAttrCount = attrInfo >>> 4;

            for (int j = 0; j < unknownAttrCount; j++) {
                processAttr();
            }

            buffer.putShort(attrCountPosition, unknownAttrCount + Integer.bitCount(attrInfo & 15));

            buffer.putInt(lengthPosition, buffer.pos - lengthPosition - 4);
        }

        private void processAttr() throws IOException {
            int nameIndex = utfInterval.readIndexCompact(defDataIn);
            buffer.putShort(nameIndex);

            int length = defDataIn.readInt();
            buffer.putInt(length);
            buffer.readFully(defDataIn, length);
        }

        private void processSignatureAttr() throws IOException {
            buffer.putShort(putPredefinedGeneratedString(Utils.PS_SIGNATURE));
            buffer.putInt(2);
            buffer.putShort(utfInterval.readIndexCompact(defDataIn));
        }

        private void processEnclosingMethodAttr() throws IOException {
            buffer.putShort(putGeneratedStr(Utils.C_EnclosingMethod));
            buffer.putInt(4);

            if (in.readBoolean()) {
                int classNameIndex = putGeneratedStr(Utils.generateEnclosingClassName(className));
                buffer.putShort(findClassIndexByName(classNameIndex));
            }
            else {
                buffer.putShort(classesInterval.readIndexCompact(in));
            }

            buffer.putShort(nameAndTypeInterval.readIndexCompactNullable(in));
        }

        private int findClassIndexByName(int nameIndex) {
            int res = 1;
            while (buffer.getShort(4 + 4 + 2 + 1 + res * 3) != nameIndex) {
                res++;
            }

            return res + 1;
        }

        private void processLineNumbersAttr(int codeLength) throws IOException {
            buffer.putShort(putPredefinedGeneratedString(Utils.PS_LINE_NUMBER_TABLE));

            if (maxLineNumberBits == 0) {
                maxLineNumberBits = in.readBits(4) + 1;
            }

            int prevLineNumber = in.readBits(maxLineNumberBits);
            int firstCodePos = 0;
            if (in.readBoolean()) {
                firstCodePos = in.readLimitedShort(codeLength - 1);
            }

            buffer.putShort(buffer.pos + 4 + 2 + 2, prevLineNumber);

            int count = 1;

            if (!in.readBoolean()) { // one line only
                int pos = buffer.pos + 4 + 2 + 2 + 4;

                do {
                    int x = defDataIn.read();
                    if (x >= 127) {
                        if (x == 127) {
                            break;
                        }

                        if (x == 128) {
                            prevLineNumber = in.readBits(maxLineNumberBits);
                            x = 0;
                        }
                        else {
                            x = ((byte)x); // make x negative
                        }
                    }

                    prevLineNumber += x;

                    buffer.putShort(pos, prevLineNumber);
                    pos += 4;
                } while (true);

                count += (pos - (buffer.pos + 4 + 2 + 2 + 4)) >>> 2;
            }

            buffer.putInt(2 + count * 4);
            buffer.putShort(count);

            buffer.putShort(firstCodePos); // first code position always 0
            buffer.skip(2);

            int prevCodePos = firstCodePos;
            for (int i = 1; i < count; i++) {
                int x = in.readBits(5);
                if (x >= 29) {
                    if (x == 29) {
                        x = 29 + in.readBits(4);
                    }
                    else if (x == 30) {
                        x = 45 + in.readBits(7);
                    }
                    else {
                        assert x == 31;
                        x = in.readUnsignedShort();
                    }
                }

                prevCodePos += x + 1;
                buffer.putShort(prevCodePos); // first code position always 0
                buffer.skip(2);
            }
        }

        private void processStackMapAttr() throws IOException {
            buffer.putShort(putPredefinedGeneratedString(Utils.PS_STACK_MAP_TABLE));
            int attrSize = Utils.readSmallShort3(defDataIn);
            buffer.putInt(attrSize);
            buffer.readFully(defDataIn, attrSize);
        }

        private void processLocalVarTableAttr(int codeLen, int maxLocals, boolean hasTypeAttribute) throws IOException {
            buffer.putShort(putPredefinedGeneratedString(Utils.PS_LOCAL_VARIABLE_TABLE));

            int attrSizePos = buffer.pos;

            int varInfo = defDataIn.read();

            buffer.skip(4 + 2);

            int index = 0;

            if ((varInfo & 1) != 0) {
                buffer.putShort(0);
                buffer.putShort(codeLen);
                buffer.putShort(putPredefinedGeneratedString(Utils.PS_THIS));

                if (classTypeIndex == 0) {
                    classTypeIndex = putGeneratedStr('L' + className + ';');
                }
                buffer.putShort(classTypeIndex);
                buffer.putShort(0);
                index = 1;
            }

            int paramCount = (varInfo >>> 1) & 7;
            int plainVarCount = varInfo >>> 4;

            int pos = buffer.pos;

            while (true) {
                int descrIndex = Utils.readLimitedShort(defDataIn, utfInterval.count);
                if (descrIndex == 0) {
                    break;
                }

                buffer.putShort(pos + 2 + 2, utfInterval.readIndexCompact(in));
                buffer.putShort(pos + 2 + 2 + 2, descrIndex - 1 + utfInterval.firstIndex);

                pos += 2 * 5;
            }

            for (int i = 0; i < paramCount; i++) {
                buffer.putShort(0);
                buffer.putShort(codeLen);
                buffer.skip(2 + 2);
                buffer.putShort(index++);
            }

            for (int i = 0; i < plainVarCount; i++) {
                int codePos = in.readLimitedShort(codeLen);
                assert codePos <= codeLen;

                buffer.putShort(codePos);
                buffer.putShort(codeLen - codePos);
                buffer.skip(2 + 2);
                buffer.putShort(index++);
            }

            while (buffer.pos != pos) {
                int codePos = in.readLimitedShort(codeLen);
                assert codePos < codeLen;

                buffer.putShort(codePos);

                int end = Utils.readLimitedShort(defDataIn, codeLen);
                assert end >= codePos;
                buffer.putShort(end - codePos);

                buffer.skip(2 + 2);

                buffer.putShort(in.readLimitedShort(maxLocals));
                index++;
            }

            int attrSize = buffer.pos - attrSizePos - 4;
            buffer.putInt(attrSizePos, attrSize);
            buffer.putShort(attrSizePos + 4, index);

            assert attrSize == 2 + index * 2 * 5;

            if (Utils.CHECK_LIMITS) {
                assert defDataIn.read() == 125;
                assert in.read() == 125;
            }

            if (hasTypeAttribute) {
                pos = attrSizePos + 4 + 2;
                buffer.putShort(putPredefinedGeneratedString(Utils.PS_LOCAL_VARIABLE_TYPE_TABLE));

                int pos2 = buffer.pos + 4 + 2;

                int elementCount = 0;

                for (int i = 0; i < index; i++) {
                    if (in.readBoolean()) {
                        System.arraycopy(buffer.array, pos, buffer.array, pos2, 10);
                        buffer.putShort(pos2 + 2*3, utfInterval.readIndexCompact(defDataIn));
                        pos2 += 10;

                        elementCount++;
                    }

                    pos += 10;
                }

                buffer.putInt(pos2 - buffer.pos - 4);
                buffer.putShort(elementCount);
                buffer.pos = pos2;
            }
        }

        private void processConstantValueAttr() throws IOException {
            buffer.putShort(putPredefinedGeneratedString(Utils.PS_CONST_VALUE));
            buffer.putInt(2);

            buffer.putShort(Utils.readSmallShort3(defDataIn));
        }

        private void processExceptionAttr() throws IOException {
            buffer.putShort(putPredefinedGeneratedString(Utils.PS_EXCEPTIONS));

            int savedPosition = buffer.pos;
            buffer.skip(4 + 2);

            do {
                int classIndex = Utils.readLimitedShort(defDataIn, classesInterval.count);
                if (classIndex == 0) break;

                buffer.putShort(classIndex);
            } while (true);

            buffer.putInt(savedPosition, buffer.pos - savedPosition - 4);
            buffer.putShort(savedPosition + 4, (buffer.pos - savedPosition - 4 - 2) >>> 1);
        }

        private void readCode() throws IOException {
            byte[] array = buffer.array;
            int pos = buffer.pos;

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
                        defDataIn.readFully(array, pos, len);

                        pos += len;
                        break;

                    case InsnTypes.TABL_INSN: {
                        pos += (4 - ((pos - buffer.pos) & 3)) & 3; // skips 0 to 3 padding bytes

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
                        pos += (4 - ((pos - buffer.pos) & 3)) & 3; // skips 0 to 3 padding bytes

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
                            int imethIndex = ref + imethodInterval.firstIndex;
                            buffer.putShort(pos, imethIndex);
                            pos += 2;

                            array[pos++] = (byte) defDataIn.read();
                            pos++; // put 0
                        }
                        else {
                            if (opcode == 180 /*Opcodes.GETFIELD*/ || opcode == 181 /*Opcodes.PUTFIELD*/
                                    || opcode == 178 /*Opcodes.GETSTATIC*/ || opcode == 179 /*Opcodes.PUTSTATIC*/) {
                                ref += fieldInterval.firstIndex;
                            } else if (opcode == 182/*Opcodes.INVOKEVIRTUAL*/ || opcode == 183/*Opcodes.INVOKESPECIAL*/
                                    || opcode == 184 /*Opcodes.INVOKESTATIC*/) {
                                ref += methodInterval.firstIndex;
                            }
                            else {
                                throw new UnsupportedOperationException(String.valueOf(opcode));
                            }

                            buffer.putShort(pos, ref);
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

            buffer.pos = pos;
        }
    }
}
