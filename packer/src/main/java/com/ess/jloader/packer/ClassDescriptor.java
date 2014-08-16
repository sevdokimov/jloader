package com.ess.jloader.packer;

import com.ess.jloader.packer.consts.*;
import com.ess.jloader.utils.*;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
* @author Sergey Evdokimov
*/
public class ClassDescriptor {

    public final ClassReader classReader;

    private final String className;

    private final Collection<AbstractConst> consts;

    public BitOutputStream plainData;
    public OpenByteOutputStream forCompressionDataArray;

    private int firstUtfIndex;
    private int firstNameAndTypeIndex;
    private int constCount;
    private LimitNumberWriter constCountLimiter;

    private int firstFieldIndex;
    private List<ConstField> constFields;

    private int firstMethodIndex;
    private List<ConstMethod> constMethods;

    private int firstIMethodIndex;
    private List<ConstInterface> constInterfaces;

    private final Set<String> generatedStr;

    private List<String> allUtf;
    private LimitNumberWriter utfLimiter;
    private Map<String, Integer> utf2index;

    private List<ConstClass> constClasses;
    private LimitNumberWriter constClassesLimiter;
    private List<ConstNameAndType> constNameAndType;

    private boolean[] predefinedUtfFlags = new boolean[Utils.PREDEFINED_UTF.length];
    private boolean hasSourceFileAttr;

    private final int[] predefinedUtfIndexes = new int[Utils.PREDEFINED_UTF.length];

    private int anonymousClassCount;
    private int firstAnonymousNameIndex;

    private final ClassNode classNode;

    public ClassDescriptor(ClassReader classReader) {
        this.classReader = classReader;

        className = classReader.getClassName();

        consts = Resolver.resolveAll(classReader, true);
        constCount = getConstPoolSize(consts);
        constCountLimiter = LimitNumberWriter.create(constCount);

        generatedStr = new LinkedHashSet<String>();
        generatedStr.add(className);

        List<String> predefinedStr = Arrays.asList(Utils.PREDEFINED_UTF);
        for (AbstractConst aConst : consts) {
            if (aConst instanceof ConstUtf) {
                String s = ((ConstUtf) aConst).getValue();
                if (generatedStr.contains(s)) continue;

                int idx = predefinedStr.indexOf(s);

                if (idx >= 0) {
                    predefinedUtfFlags[idx] = true;
                }
            }
        }

        for (int i = 0; i < Utils.PREDEFINED_UTF.length; i++) {
            if (predefinedUtfFlags[i]) {
                predefinedUtfIndexes[i] = generatedStr.size();
                generatedStr.add(Utils.PREDEFINED_UTF[i]);
            }
        }

        classNode = new ClassNode();
        classReader.accept(classNode, 0);
        if (classNode.sourceFile != null) {
            String sourceFileName = Utils.generateSourceFileName(className);
            if (sourceFileName.equals(classNode.sourceFile)) {
                hasSourceFileAttr = true;
                generatedStr.add("SourceFile");
                generatedStr.add(sourceFileName);
            }
        }

        anonymousClassCount = PackUtils.evaluateAnonymousClassCount(classNode);
        if (anonymousClassCount > 0xFF) throw new InvalidJarException();
        firstAnonymousNameIndex = generatedStr.size();
        for (int i = 1; i <= anonymousClassCount; i++) {
            generatedStr.add(className + '$' + i);
        }
    }

    private void writeClassSize(BitOutputStream out, int size) throws IOException {
        if (size < 0x8000) {
            out.writeShort(size);
        }
        else {
            out.writeShort(-(size >>> 15));
            out.writeShort(size & 0x7FFF);
        }
    }

    public Set<String> getGeneratedStr() {
        return generatedStr;
    }

    public Collection<AbstractConst> getConsts() {
        return consts;
    }

    public ClassReader getClassReader() {
        return classReader;
    }

    public List<ConstClass> getConstClasses() {
        return constClasses;
    }

    private byte[] repack(ClassReader classReader,
                          Collection<ConstClass> constClasses,
                          Collection<ConstField> constField,
                          Collection<ConstInterface> constInteface,
                          Collection<ConstMethod> constMethod,
                          Collection<ConstNameAndType> constNameAndTypes,
                          Collection<String> utfs) {
        ClassWriter classWriter = new ClassWriter(0);
        ClassWriterManager classWriterManager = new ClassWriterManager(classWriter, constCount);

        classWriterManager.goHead(utfs.size());
        for (String utf : utfs) {
            classWriter.newUTF8(utf);
        }

        classWriterManager.toWriterTop(constNameAndTypes);

        classWriterManager.goBack();
        classWriterManager.toWriter(constClasses);

        classWriterManager.toWriterTop(constMethod);

        classWriterManager.toWriterTop(constInteface);

        classWriterManager.toWriterTop(constField);

        classWriterManager.goBack();

        classReader.accept(classWriter, 0);
        classWriterManager.finish();

        return classWriter.toByteArray();
    }

    public void pack(CompressionContext ctx) throws IOException {
        plainData = new BitOutputStream(new ByteArrayOutputStream());
        forCompressionDataArray = new OpenByteOutputStream();

        DataOutputStream compressed = new DataOutputStream(forCompressionDataArray);

        plainData.writeBoolean(hasSourceFileAttr);

        Set<String> packedStr = new LinkedHashSet<String>();
        List<String> notPackedStr = new ArrayList<String>();

        constClasses = new ArrayList<ConstClass>();
        constNameAndType = new ArrayList<ConstNameAndType>();

        constMethods = new ArrayList<ConstMethod>();
        constInterfaces = new ArrayList<ConstInterface>();
        constFields = new ArrayList<ConstField>();

        for (AbstractConst aConst : consts) {
            if (aConst.getTag() == ConstUtf.TAG) {
                String s = ((ConstUtf)aConst).getValue();

                if (!generatedStr.contains(s)) {
                    if (ctx.getLiteralsCache().getHasString(s)) {
                        packedStr.add(s);
                    }
                    else {
                        notPackedStr.add(s);
                    }
                }
            }
            else if (aConst.getTag() == ConstClass.TAG) {
                constClasses.add((ConstClass) aConst);
            }
            else if (aConst.getTag() == ConstNameAndType.TAG) {
                constNameAndType.add((ConstNameAndType) aConst);
            }
            else if (aConst instanceof ConstMethod) {
                constMethods.add((ConstMethod) aConst);
            }
            else if (aConst instanceof ConstInterface) {
                constInterfaces.add((ConstInterface) aConst);
            }
            else if (aConst instanceof ConstField) {
                constFields.add((ConstField) aConst);
            }
        }

        Collections.sort(notPackedStr);

        moveToBegin(constClasses, 0, new ConstClass(className));
        moveToBegin(constClasses, 1, new ConstClass(classReader.getSuperName()));

        allUtf = Lists.newArrayList(Iterables.concat(generatedStr, packedStr, notPackedStr));
        utfLimiter = LimitNumberWriter.create(allUtf.size() - 1);

        utf2index = new HashMap<String, Integer>();
        for (int i = 0; i < allUtf.size(); i++) {
            utf2index.put(allUtf.get(i), i);
        }

        firstUtfIndex = constCount - allUtf.size();
        firstNameAndTypeIndex = firstUtfIndex - constNameAndType.size();
        firstMethodIndex = firstNameAndTypeIndex - constMethods.size();
        firstIMethodIndex = firstMethodIndex - constInterfaces.size();
        firstFieldIndex = firstIMethodIndex - constFields.size();

        byte[] classBytes = repack(classReader, constClasses, constFields, constInterfaces, constMethods,
                constNameAndType, allUtf);

        ByteBuffer buffer = ByteBuffer.wrap(classBytes);

        writeClassSize(plainData, classBytes.length);

        buffer.getInt(); // Skip 0xCAFEBABE

        int version = buffer.getInt();
        plainData.writeBits(ctx.getVersionCache().getVersionIndex(version), 3);

        if ((buffer.getShort() & 0xFFFF) != constCount) {
            throw new RuntimeException();
        }

        plainData.writeSmall_0_3_8_16(anonymousClassCount);

        PackUtils.writeSmallShort3(plainData, constCount);

        constCountLimiter.write(plainData, allUtf.size());

        utfLimiter.write(plainData, constClasses.size());
        constClassesLimiter = LimitNumberWriter.create(constClasses.size() - 1);

        PackUtils.writeSmallShort3(plainData, constFields.size());
        PackUtils.writeSmallShort3(plainData, constInterfaces.size());
        PackUtils.writeSmallShort3(plainData, constMethods.size());
        PackUtils.writeSmallShort3(plainData, constNameAndType.size());

        skipClassConst(buffer, className);

        // First const is class of current class
        for (int i = 1; i < constClasses.size(); i++) {
            int utfIndex = skipClassConst(buffer, constClasses.get(i).getType());

            writeUtfIndex(plainData, utfIndex);
        }

        utfLimiter.write(plainData, packedStr.size());

        copyConstTableTail(buffer, constCount - 1 - constClasses.size()
                - constFields.size() - constInterfaces.size() - constMethods.size()
                - constNameAndType.size()
                - allUtf.size(), compressed);

        for (ConstAbstractRef ref : Iterables.concat(constFields, constInterfaces, constMethods)) {
            int tag = buffer.get();
            assert tag == ref.getTag();

            int classIndex = buffer.getShort() & 0xFFFF;
            assert constClasses.get(classIndex - 1).getType().equals(ref.getOwner().getInternalName());
            constClassesLimiter.write(plainData, classIndex - 1);

            int nameTypeIndex = buffer.getShort() & 0xFFFF;
            assert nameTypeIndex >= firstNameAndTypeIndex && nameTypeIndex < firstNameAndTypeIndex + constNameAndType.size();
            PackUtils.writeLimitedNumber(compressed, nameTypeIndex - firstNameAndTypeIndex, constNameAndType.size());
        }

        for (ConstNameAndType nameAndType : constNameAndType) {
            int tag = buffer.get();
            assert tag == ConstNameAndType.TAG;

            copyUtfIndex(buffer, compressed, nameAndType.getName());
            copyUtfIndex(buffer, compressed, nameAndType.getDescr());
        }

        for (boolean b : predefinedUtfFlags) {
            plainData.writeBoolean(b);
        }

        for (String s : generatedStr) {
            skipUtfConst(buffer, s);
        }

        HuffmanOutputStream<String> h = ctx.getLiteralsCache().createHuffmanOutput();
        h.reset(plainData);
        for (String s : packedStr) {
            skipUtfConst(buffer, s);
            h.write(s);
        }

        for (String s : notPackedStr) {
            compressed.writeUTF(s);
            skipUtfConst(buffer, s);
        }

        int accessFlags = buffer.getShort();
        compressed.writeShort(accessFlags);

        int thisClassIndex = buffer.getShort();
        if (thisClassIndex != 1) throw new RuntimeException(String.valueOf(thisClassIndex));

        int superClassIndex = buffer.getShort();
        if (superClassIndex != 2) throw new RuntimeException(String.valueOf(thisClassIndex));

        processInterfaces(buffer, plainData);
        processFields(buffer, compressed);
        processMethods(buffer, compressed);
        processClassAttributes(buffer, compressed);

        assert !buffer.hasRemaining();

        plainData.finish();
    }

    private void processInterfaces(ByteBuffer buffer, BitOutputStream plainData) throws IOException {
        int interfaceCount = buffer.getShort() & 0xFFFF;

        assert interfaceCount == classNode.interfaces.size();

        plainData.writeSmall_0_3_8_16(interfaceCount);

        for (int i = 0; i < interfaceCount; i++) {
            int classIndex = buffer.getShort();
            constClassesLimiter.write(plainData, classIndex - 1);
        }
    }

    public String getUtfByIndex(int index) {
        return allUtf.get(index - firstUtfIndex);
    }

    public int getIndexByUtf(String utf) {
        return utf2index.get(utf) + firstUtfIndex;
    }

    private void processFields(ByteBuffer buffer, DataOutputStream out) throws IOException {
        int fieldCount = buffer.getShort() & 0xFFFF;
        PackUtils.writeSmallShort3(out, fieldCount);

        for (int i = 0; i < fieldCount; i++) {
            short accessFlags = buffer.getShort();
            out.writeShort(accessFlags);

            int nameIndex = buffer.getShort() & 0xFFFF;
            writeUtfIndex(out, nameIndex);

            int descrIndex = buffer.getShort() & 0xFFFF;
            writeUtfIndex(out, descrIndex);

            List<Attribute> attributes = AttributeUtils.readAllAttributes(AttributeType.FIELD, this, buffer);

            PackUtils.writeSmallShort3(out, attributes.size());

            for (Attribute attribute : attributes) {
                writeUtfIndex(out, getIndexByUtf(attribute.getName()));
                attribute.writeTo(out, this);
            }
        }
    }

    private void processMethods(ByteBuffer buffer, DataOutputStream out) throws IOException {
        int methodCount = buffer.getShort() & 0xFFFF;
        PackUtils.writeSmallShort3(out, methodCount);

        for (int i = 0; i < methodCount; i++) {
            int accessFlags = buffer.getShort();
            out.writeShort(accessFlags);

            int nameIndex = buffer.getShort() & 0xFFFF;
            writeUtfIndex(out, nameIndex);

            int descrIndex = buffer.getShort() & 0xFFFF;
            writeUtfIndex(out, descrIndex);

            List<Attribute> attributes = AttributeUtils.readAllAttributes(AttributeType.METHOD, this, buffer);

            PackUtils.writeSmallShort3(out, attributes.size());

            if (!Modifier.isNative(accessFlags) && !Modifier.isAbstract(accessFlags)) {
                Attribute code = AttributeUtils.removeAttributeByName(attributes, "Code");

                code.writeTo(out, this);
            }
            else {
                assert AttributeUtils.findAttributeByName(attributes, "Code") == null;
            }

            for (Attribute attribute : attributes) {
                writeUtfIndex(out, getIndexByUtf(attribute.getName()));
                attribute.writeTo(out, this);
            }
        }
    }

    private void processClassAttributes(ByteBuffer buffer, DataOutputStream out) throws IOException {
        List<Attribute> attributes = AttributeUtils.readAllAttributes(AttributeType.CLASS, this, buffer);

        PackUtils.writeSmallShort3(out, attributes.size());

        for (Attribute attribute : attributes) {
            if (hasSourceFileAttr && attribute.getName().equals("SourceFile")) {
                continue;
            }

            writeUtfIndex(out, getIndexByUtf(attribute.getName()));
            attribute.writeTo(out, this);
        }
    }


    private <T> void moveToBegin(List<T> list, int beginIndex, T element) {
        int oldIndex = list.indexOf(element);
        assert oldIndex >= beginIndex;

        for (int i = oldIndex; i > beginIndex; i--) {
            list.set(i, list.get(i - 1));
        }

        list.set(beginIndex, element);
    }

    public void writeTo(OutputStream out, byte[] dictionary) throws IOException {
        ByteArrayOutputStream plainDataArray = (ByteArrayOutputStream) plainData.getDelegate();
        if (plainDataArray.size() > 0xFFFF) throw new InvalidJarException();

        new DataOutputStream(out).writeShort(plainDataArray.size());

        plainDataArray.writeTo(out);

        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setDictionary(dictionary);

        DeflaterOutputStream defOut = new DeflaterOutputStream(out, deflater);
        forCompressionDataArray.writeTo(defOut);
        defOut.close();
    }

    private int skipClassConst(ByteBuffer buffer, String className) {
        int tag = buffer.get();
        if (tag != ConstClass.TAG) throw new RuntimeException("" + tag);

        int utfIndex = buffer.getShort();
        assert className.equals(allUtf.get(utfIndex - firstUtfIndex));

        return utfIndex;
    }

    private void skipUtfConst(ByteBuffer buffer, String value) {
        int tag = buffer.get();
        if (tag != 1) throw new RuntimeException("" + tag);
        if (!value.equals(PackUtils.readUtf(buffer.array(), buffer.position()))) {
            throw new RuntimeException();
        }

        int strSize = buffer.getShort() & 0xFFFF;
        buffer.position(buffer.position() + strSize);
    }

    public void writeUtfIndex(BitOutputStream out, int utfIndex) throws IOException {
        assert utfIndex >= firstUtfIndex;
        utfLimiter.write(out, utfIndex - firstUtfIndex);
    }

    public void writeUtfIndex(DataOutput out, int utfIndex) throws IOException {
        assert utfIndex >= firstUtfIndex;
        PackUtils.writeLimitedNumber(out, utfIndex - firstUtfIndex, allUtf.size() - 1);
    }

    private void copyUtfIndex(ByteBuffer buffer, DataOutputStream out) throws IOException {
        copyUtfIndex(buffer, out, null);
    }

    private void copyUtfIndex(ByteBuffer buffer, BitOutputStream out, @Nullable String expectedValue) throws IOException {
        int utfIndex = buffer.getShort() & 0xFFFF;
        writeUtfIndex(out, utfIndex);

        if (expectedValue != null) {
            assert allUtf.get(utfIndex - firstUtfIndex).equals(expectedValue);
        }
    }

    private void copyUtfIndex(ByteBuffer buffer, DataOutputStream out, @Nullable String expectedValue) throws IOException {
        int utfIndex = buffer.getShort() & 0xFFFF;
        writeUtfIndex(out, utfIndex);

        if (expectedValue != null) {
            assert allUtf.get(utfIndex - firstUtfIndex).equals(expectedValue);
        }
    }

    private void copyConstTableTail(ByteBuffer buffer, int constCount, DataOutputStream out) throws IOException {
        for (int i = 0; i < constCount; i++) {
            int tag = buffer.get();

            out.write(tag);

            switch (tag) {
                case 9: // ClassWriter.FIELD:
                case 10: // ClassWriter.METH:
                case 11: // ClassWriter.IMETH:
                    int classIndex = buffer.getShort();
                    PackUtils.writeLimitedNumber(out, classIndex - 1, constClasses.size() - 1);
                    int nameTypeIndex = buffer.getShort();
                    PackUtils.writeLimitedNumber(out, nameTypeIndex - firstNameAndTypeIndex, constNameAndType.size() - 1);
                    break;

                case 3: // ClassWriter.INT:
                case 4: // ClassWriter.FLOAT:
                case 18: // ClassWriter.INDY:
                    out.write(buffer.array(), buffer.position(), 4);
                    buffer.position(buffer.position() + 4);
                    break;

                case 12: // ClassWriter.NAME_TYPE:
                    copyUtfIndex(buffer, out);
                    copyUtfIndex(buffer, out);
                    break;

                case 5:// ClassWriter.LONG:
                case 6: // ClassWriter.DOUBLE:
                    out.write(buffer.array(), buffer.position(), 8);
                    buffer.position(buffer.position() + 8);
                    ++i;
                    break;

                case 15: // ClassWriter.HANDLE:
                    out.write(buffer.array(), buffer.position(), 3);
                    buffer.position(buffer.position() + 3);
                    break;

                case 8: // ClassWriter.STR
                case 16: // ClassWriter.MTYPE
                    copyUtfIndex(buffer, plainData, null);
                    break;

                default:
                    throw new RuntimeException(String.valueOf(tag));
            }
        }
    }

    private static int getConstPoolSize(Collection<AbstractConst> consts) {
        int res = consts.size() + 1;

        for (AbstractConst aConst : consts) {
            if (aConst.getTag() == ConstLong.TAG || aConst.getTag() == ConstDouble.TAG) {
                res++;
            }
        }

        return res;
    }

    public int formatFiledIndex(int index) {
        assert index >= firstFieldIndex;
        assert index < firstFieldIndex + constFields.size();

        return index - firstFieldIndex;
    }

    public int formatMethodIndex(int index) {
        assert index >= firstMethodIndex;
        assert index < firstMethodIndex + constMethods.size();

        return index - firstMethodIndex;
    }

    public int formatIMethodIndex(int index) {
        assert index >= firstIMethodIndex;
        assert index < firstIMethodIndex + constInterfaces.size();

        return index - firstIMethodIndex;
    }

    public int getAnonymousClassCount() {
        return anonymousClassCount;
    }

    @Override
    public String toString() {
        return className;
    }
}
