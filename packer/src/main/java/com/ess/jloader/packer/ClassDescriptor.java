package com.ess.jloader.packer;

import com.ess.jloader.packer.attributes.*;
import com.ess.jloader.packer.consts.*;
import com.ess.jloader.utils.*;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
* @author Sergey Evdokimov
*/
public class ClassDescriptor extends PropertiesHolder {

    private final ClassReader classReader;

    private final String className;

    private final Collection<AbstractConst> consts;

    public BitOutputStream plainData;
    public OpenByteOutputStream forCompressionDataArray;

    private int constCount;

    private ConstIndexInterval classesInterval;
    private List<ConstClass> constClasses;

    private ConstIndexInterval fieldInterval;
    private List<ConstField> constFields;

    private ConstIndexInterval imethodInterval;
    private List<ConstInterface> constInterfaces;

    private ConstIndexInterval methodInterval;
    private List<ConstMethod> constMethods;

    private ConstIndexInterval nameAndTypeInterval;
    private List<ConstNameAndType> constNameAndType;

    private ConstIndexInterval utfInterval;

    private List<String> allUtf;
    private Map<String, Integer> utf2index;

    private final Set<String> generatedStr;

    private byte[] repackedClass;

    public ClassDescriptor(ClassReader classReader) {
        this.classReader = classReader;

        className = classReader.getClassName();

        consts = Resolver.resolveAll(classReader);
        consts.retainAll(Resolver.resolveAll(PackUtils.repack(classReader)));

        constCount = PackUtils.getConstPoolSize(consts);

        generatedStr = GenerateStrCollector.collectGeneratedStr(classReader, consts);
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
        assert plainData == null;

        plainData = new BitOutputStream(new ByteArrayOutputStream());
        forCompressionDataArray = new OpenByteOutputStream();

        DataOutputStream compressed = new DataOutputStream(forCompressionDataArray);

        List<String> packedStr = new ArrayList<String>();
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

        utf2index = new HashMap<String, Integer>();
        for (int i = 0; i < allUtf.size(); i++) {
            utf2index.put(allUtf.get(i), i);
        }

        utfInterval = ConstIndexInterval.create(constCount, allUtf.size());
        nameAndTypeInterval = new ConstIndexInterval(utfInterval, constNameAndType.size());
        methodInterval = new ConstIndexInterval(nameAndTypeInterval, constMethods.size());
        imethodInterval = new ConstIndexInterval(methodInterval, constInterfaces.size());
        fieldInterval = new ConstIndexInterval(imethodInterval, constFields.size());

        classesInterval = new ConstIndexInterval(1, constClasses.size());

        repackedClass = repack(classReader, constClasses, constFields, constInterfaces, constMethods,
                constNameAndType, allUtf);

        ByteBuffer buffer = ByteBuffer.wrap(repackedClass);

        writeClassSize(plainData, repackedClass.length);

        buffer.getInt(); // Skip 0xCAFEBABE

        int version = buffer.getInt();
        plainData.writeBits(ctx.getVersionCache().getVersionIndex(version), 3);

        if ((buffer.getShort() & 0xFFFF) != constCount) {
            throw new RuntimeException();
        }

        PackUtils.writeSmallShort3(plainData, constCount);

        plainData.writeLimitedShort(allUtf.size(), constCount);
        PackUtils.writeSmallShort3(plainData, constNameAndType.size());
        PackUtils.writeSmallShort3(plainData, constMethods.size());
        PackUtils.writeSmallShort3(plainData, constInterfaces.size());
        PackUtils.writeSmallShort3(plainData, constFields.size());

        plainData.writeLimitedShort(constClasses.size(), utfInterval.getCount());

        skipClassConst(buffer, className);

        // First const is class of current class
        for (int i = 1; i < constClasses.size(); i++) {
            int utfIndex = skipClassConst(buffer, constClasses.get(i).getType());

            utfInterval.writeIndexCompact(plainData, utfIndex);
        }

        copyConstTableTail(buffer, constCount - 1 - constClasses.size()
                - constFields.size() - constInterfaces.size() - constMethods.size()
                - constNameAndType.size()
                - allUtf.size(), compressed);

        for (ConstAbstractRef ref : Iterables.concat(constFields, constInterfaces, constMethods)) {
            int tag = buffer.get();
            assert tag == ref.getTag();

            int classIndex = buffer.getShort() & 0xFFFF;
            assert constClasses.get(classIndex - 1).getType().equals(ref.getOwner().getInternalName());
            classesInterval.writeIndexCompact(plainData, classIndex);

            int nameTypeIndex = buffer.getShort() & 0xFFFF;
            nameAndTypeInterval.writeIndexCompact(plainData, nameTypeIndex);
        }

        for (ConstNameAndType nameAndType : constNameAndType) {
            int tag = buffer.get();
            assert tag == ConstNameAndType.TAG;

            copyUtfIndex(buffer, plainData, nameAndType.getName());
            copyUtfIndex(buffer, plainData, nameAndType.getDescr());
        }

        int generatedStrPosition = buffer.position();
        for (String s : generatedStr) {
            skipUtfConst(buffer, s);
        }
        PackUtils.writeSmallShort3(plainData, buffer.position() - generatedStrPosition);

        for (String utf : Utils.COMMON_UTF) {
            plainData.writeBit(generatedStr.contains(utf));
        }

        plainData.writeLimitedShort(packedStr.size(), utfInterval.getCount());
        HuffmanOutputStream<String> h = ctx.getLiteralsCache().createHuffmanOutput();
        h.reset(plainData);
        for (String s : packedStr) {
            skipUtfConst(buffer, s);
            h.write(s);
        }

        plainData.writeLimitedShort(notPackedStr.size(), utfInterval.getCount());
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

        processInterfaces(buffer);
        processFields(buffer, compressed);
        processMethods(buffer, compressed);
        processClassAttributes(buffer, compressed);

        assert !buffer.hasRemaining();

        plainData.finish();
    }

    private void processInterfaces(ByteBuffer buffer) throws IOException {
        int interfaceCount = buffer.getShort() & 0xFFFF;

        assert interfaceCount == classReader.getInterfaces().length;

        plainData.writeSmall_0_3_8_16(interfaceCount);

        for (int i = 0; i < interfaceCount; i++) {
            int classIndex = buffer.getShort() & 0xFFFF;
            classesInterval.writeIndexCompact(plainData, classIndex);
        }
    }

    public String getUtfByIndex(int index) {
        return allUtf.get(index - utfInterval.getFirstIndex());
    }

    public int getIndexByUtf(String utf) {
        return utf2index.get(utf) + utfInterval.getFirstIndex();
    }

    private void processFields(ByteBuffer buffer, DataOutputStream out) throws IOException {
        int fieldCount = buffer.getShort() & 0xFFFF;
        PackUtils.writeSmallShort3(out, fieldCount);

        for (int i = 0; i < fieldCount; i++) {
            short accessFlags = buffer.getShort();
            out.writeShort(accessFlags);

            int nameIndex = buffer.getShort() & 0xFFFF;
            utfInterval.writeIndexCompact(plainData, nameIndex);

            int descrIndex = buffer.getShort() & 0xFFFF;
            utfInterval.writeIndexCompact(plainData, descrIndex);

            // Process attributes
            List<Attribute> attributes = AttributeUtils.readAllAttributes(FieldAttributeFactory.INSTANCE, new AttrContext(this), buffer);

            List<Attribute> knownAttributes = new ArrayList<Attribute>();

            int attrInfo = AttributeUtils.extractKnownAttributes(attributes, knownAttributes, "Signature", "ConstantValue");

            PackUtils.writeSmallShort3(out, attrInfo);

            for (Attribute attribute : knownAttributes) {
                attribute.writeTo(out, plainData, this);
            }

            for (Attribute attribute : attributes) {
                utfInterval.writeIndexCompact(out, getIndexByUtf(attribute.getName()));
                attribute.writeTo(out, plainData, this);
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
            utfInterval.writeIndexCompact(plainData, nameIndex);

            int descrIndex = buffer.getShort() & 0xFFFF;
            utfInterval.writeIndexCompact(plainData, descrIndex);

            List<Attribute> attributes = AttributeUtils.readAllAttributes(MethodAttributeFactory.INSTANCE, new AttrContext(this), buffer);

            Attribute code = AttributeUtils.removeAttributeByName(attributes, "Code");

            List<Attribute> knownAttributes = new ArrayList<Attribute>();

            int attrInfo = AttributeUtils.extractKnownAttributes(attributes, knownAttributes, "Signature", "Exceptions");

            PackUtils.writeSmallShort3(out, attrInfo);

            if (!Modifier.isNative(accessFlags) && !Modifier.isAbstract(accessFlags)) {
                assert code != null;
                code.writeTo(out, plainData, this);
            }
            else {
                assert code == null;
            }

            for (Attribute attribute : knownAttributes) {
                attribute.writeTo(out, plainData, this);
            }

            for (Attribute attribute : attributes) {
                utfInterval.writeIndexCompact(out, getIndexByUtf(attribute.getName()));
                attribute.writeTo(out, plainData, this);
            }
        }
    }

    private void processClassAttributes(ByteBuffer buffer, DataOutputStream out) throws IOException {
        List<Attribute> attributes = AttributeUtils.readAllAttributes(ClassAttributeFactory.INSTANCE, new AttrContext(this), buffer);

        List<Attribute> knownAttributes = new ArrayList<Attribute>();

        int attrInfo = AttributeUtils.extractKnownAttributes(attributes, knownAttributes, "SourceFile", "InnerClasses", "Signature", "EnclosingMethod");

        PackUtils.writeSmallShort3(out, attrInfo);

        for (Attribute attribute : knownAttributes) {
            attribute.writeTo(out, plainData, this);
        }

        for (Attribute attribute : attributes) {
            utfInterval.writeIndexCompact(out, getIndexByUtf(attribute.getName()));
            attribute.writeTo(out, plainData, this);
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
        assert className.equals(allUtf.get(utfIndex - utfInterval.getFirstIndex()));

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

    private void copyUtfIndex(ByteBuffer buffer, BitOutputStream out, @Nullable String expectedValue) throws IOException {
        int utfIndex = buffer.getShort() & 0xFFFF;
        utfInterval.writeIndexCompact(out, utfIndex);

        if (expectedValue != null) {
            assert allUtf.get(utfIndex - utfInterval.getFirstIndex()).equals(expectedValue);
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
                    int classIndex = buffer.getShort() & 0xFFFF;
                    classesInterval.writeIndexCompact(out, classIndex);

                    int nameTypeIndex = buffer.getShort() & 0xFFFF;
                    nameAndTypeInterval.writeIndexCompact(out, nameTypeIndex);
                    break;

                case 3: // ClassWriter.INT:
                case 4: // ClassWriter.FLOAT:
                case 18: // ClassWriter.INDY:
                    out.write(buffer.array(), buffer.position(), 4);
                    buffer.position(buffer.position() + 4);
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
                    utfInterval.writeIndexCompact(plainData, buffer.getShort() & 0xFFFF);
                    break;

                default:
                    throw new RuntimeException(String.valueOf(tag));
            }
        }
    }

    public ConstIndexInterval getFieldInterval() {
        return fieldInterval;
    }

    public ConstIndexInterval getMethodInterval() {
        return methodInterval;
    }

    public ConstIndexInterval getImethodInterval() {
        return imethodInterval;
    }

    public ConstIndexInterval getNameAndTypeInterval() {
        return nameAndTypeInterval;
    }

    public ConstIndexInterval getUtfInterval() {
        return utfInterval;
    }

    public ConstIndexInterval getClassesInterval() {
        return classesInterval;
    }

    public String getClassName() {
        return className;
    }

    public byte[] getRepackedClass() {
        return repackedClass;
    }

    @Override
    public String toString() {
        return className;
    }
}
