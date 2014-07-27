package com.ess.jloader.packer;

import com.ess.jloader.loader.PackClassLoader;
import com.ess.jloader.packer.consts.*;
import com.ess.jloader.utils.ClassWriterManager;
import com.ess.jloader.utils.HuffmanOutputStream;
import com.ess.jloader.utils.OpenByteOutputStream;
import com.ess.jloader.utils.Utils;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
* @author Sergey Evdokimov
*/
public class ClassDescriptor {

    private static final Logger log = Logger.getLogger(ClassDescriptor.class);

    public final ClassReader classReader;

    private final String className;

    private final Collection<AbstractConst> consts;

    public OpenByteOutputStream plainDataArray;
    public OpenByteOutputStream forCompressionDataArray;

    private int firstUtfIndex;
    private int firstNameAndTypeIndex;
    private int constCount;

    private final Set<String> generatedStr;

    private List<String> allUtf;

    private List<ConstClass> constClasses;
    private List<ConstNameAndType> constNameAndType;

    private int flags = 0;

    public ClassDescriptor(ClassReader classReader) {
        this.classReader = classReader;

        className = classReader.getClassName();

        consts = Resolver.resolveAll(classReader, true);
        constCount = getConstPoolSize(consts);

        generatedStr = new LinkedHashSet<String>();
        generatedStr.add(className);
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

    private byte[] repack(ClassReader classReader,
                          Collection<ConstClass> constClasses,
                          Collection<ConstNameAndType> constNameAndTypes,
                          Collection<String> utfs) {
        ClassWriter classWriter = new ClassWriter(0);
        ClassWriterManager classWriterManager = new ClassWriterManager(classWriter, constCount);

        classWriterManager.goHead(utfs.size());
        for (String utf : utfs) {
            classWriter.newUTF8(utf);
        }

        classWriterManager.goHead(constNameAndTypes.size());
        for (ConstNameAndType aConst : constNameAndTypes) {
            aConst.toWriter(classWriter);
        }

        classWriterManager.goBack();
        for (ConstClass aConst : constClasses) {
            aConst.toWriter(classWriter);
        }

        classReader.accept(classWriter, 0);
        classWriterManager.finish();

        return classWriter.toByteArray();
    }

    public void pack(CompressionContext ctx) throws IOException {
        plainDataArray = new OpenByteOutputStream();
        forCompressionDataArray = new OpenByteOutputStream();

        DataOutputStream plainData = new DataOutputStream(plainDataArray);
        DataOutputStream compressed = new DataOutputStream(forCompressionDataArray);

        Set<String> packedStr = new LinkedHashSet<String>();
        List<String> notPackedStr = new ArrayList<String>();

        constClasses = new ArrayList<ConstClass>();
        constNameAndType = new ArrayList<ConstNameAndType>();

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
        }

        Collections.sort(notPackedStr);

        moveToBegin(constClasses, 0, new ConstClass(className));
        moveToBegin(constClasses, 1, new ConstClass(classReader.getSuperName()));

        allUtf = Lists.newArrayList(Iterables.concat(generatedStr, packedStr, notPackedStr));

        int utfCount = allUtf.size();
        firstUtfIndex = constCount - utfCount;
        firstNameAndTypeIndex = firstUtfIndex - constNameAndType.size();

        byte[] classBytes = repack(classReader, constClasses, constNameAndType, allUtf);
        ByteBuffer buffer = ByteBuffer.wrap(classBytes);

        int version = buffer.getInt(4);
        flags |= ctx.getVersionCache().getVersionIndex(version);

        if (classBytes.length > 0xFFFF) {
            flags |= Utils.F_LONG_CLASS;
        }

        buffer.position(4 + 4); // skip 0xCAFEBABE, version

        if ((buffer.getShort() & 0xFFFF) != constCount) {
            throw new RuntimeException();
        }

        if (classBytes.length > 0xFFFF) {
            plainData.writeInt(classBytes.length);
        }
        else {
            plainData.writeShort(classBytes.length);
        }

        writeSmallShort3(plainData, constCount);
        writeLimitedNumber(plainData, utfCount, constCount);
        writeSmallShort3(plainData, constClasses.size());
        writeSmallShort3(plainData, constNameAndType.size());

        skipClassConst(buffer, className);

        // First const is class of current class
        for (int i = 1; i < constClasses.size(); i++) {
            int utfIndex = skipClassConst(buffer, constClasses.get(i).getType());

            writeUtfIndex(plainData, utfIndex);
        }

        writeLimitedNumber(plainData, packedStr.size(), utfCount);

        HuffmanOutputStream<String> h = ctx.getLiteralsCache().createHuffmanOutput();
        h.reset(plainData);
        for (String s : packedStr) {
            h.write(s);
        }
        h.finish();

        copyConstTableTail(buffer, constCount - 1 - constClasses.size() - constNameAndType.size() - utfCount, compressed);

        for (ConstNameAndType nameAndType : constNameAndType) {
            int tag = buffer.get();
            assert tag == ConstNameAndType.TAG;

            copyUtfIndex(buffer, compressed, nameAndType.getName());
            copyUtfIndex(buffer, compressed, nameAndType.getDescr());
        }

        for (String s : generatedStr) {
            skipUtfConst(buffer, s);
        }
        for (String s : packedStr) {
            skipUtfConst(buffer, s);
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

        processInterfaces(buffer, compressed);
        processFields(buffer, compressed);

        compressed.write(classBytes, buffer.position(), classBytes.length - buffer.position());
    }

    private void processInterfaces(ByteBuffer buffer, DataOutputStream out) throws IOException {
        int interfaceCount = buffer.getShort() & 0xFFFF;

        if (interfaceCount <= 2) {
            flags |= interfaceCount << Utils.F_INTERFACE_COUNT_SHIFT;
        }
        else {
            if (interfaceCount > 255) throw new InvalidJarException();
            flags |= 3 << Utils.F_INTERFACE_COUNT_SHIFT;

            out.write(interfaceCount);
        }

        for (int i = 0; i < interfaceCount; i++) {
            int classIndex = buffer.getShort();
            writeLimitedNumber(out, classIndex - 3, constClasses.size() - 1);
        }
    }

    private void skipAttr(ByteBuffer buffer, DataOutputStream out) throws IOException {
        int nameIndex = buffer.getShort() & 0xFFFF;
        writeUtfIndex(out, nameIndex);

        int length = buffer.getInt();
        out.writeInt(length);
        out.write(buffer.array(), buffer.position(), length);
        buffer.position(buffer.position() + length);

        System.out.println(length);
    }

    private void processFields(ByteBuffer buffer, DataOutputStream out) throws IOException {
        int fieldCount = buffer.getShort() & 0xFFFF;
        writeSmallShort3(out, fieldCount);

        for (int i = 0; i < fieldCount; i++) {
            short accessFlags = buffer.getShort();
            out.writeShort(accessFlags);

            int nameIndex = buffer.getShort() & 0xFFFF;
            writeUtfIndex(out, nameIndex);

            int descrIndex = buffer.getShort() & 0xFFFF;
            writeUtfIndex(out, descrIndex);

            int attrCount = buffer.getShort() & 0xFFFF;
            writeSmallShort3(out, attrCount);

            for (int j = 0; j < attrCount; j++) {
                skipAttr(buffer, out);
            }
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
        DataOutputStream dataOut = new DataOutputStream(out);
        dataOut.writeInt(flags);

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

    private void writeSmallShort3(DataOutputStream out, int x) throws IOException {
        assert x <= 0xFFFF;

        if (PackClassLoader.CHECK_LIMITS) {
            out.writeByte(0x73);
        }

        if (x <= 251) {
            out.write(x);
        }
        else {
            int z = x + 4;
            int d = 251 + (z >> 8);

            if (d < 255) {
                out.write(d);
                out.write(z);
            }
            else {
                out.write(255);
                out.writeShort(x);
            }
        }
    }

    private void writeLimitedNumber(DataOutputStream out, int x, int limit) throws IOException {
        assert x >= 0;
        assert x <= limit;

        if (PackClassLoader.CHECK_LIMITS) {
            out.writeShort(limit);
        }

        if (limit == 0) {
            // data no needed
        }
        else if (limit < 256) {
            out.write(x);
        }
        else {
            out.writeShort(x);
        }
    }

    private void writeUtfIndex(DataOutputStream out, int utfIndex) throws IOException {
        assert utfIndex >= firstUtfIndex;
        writeLimitedNumber(out, utfIndex - firstUtfIndex, allUtf.size() - 1);
    }

    private void copyUtfIndex(ByteBuffer buffer, DataOutputStream out) throws IOException {
        copyUtfIndex(buffer, out, null);
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
                    writeLimitedNumber(out, classIndex - 1, constClasses.size() - 1);
                    int nameTypeIndex = buffer.getShort();
                    writeLimitedNumber(out, nameTypeIndex - firstNameAndTypeIndex, constNameAndType.size() - 1);
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

                case 7: // ClassWriter.CLASS
                case 8: // ClassWriter.STR
                case 16: // ClassWriter.MTYPE
                    copyUtfIndex(buffer, out);
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

    @Override
    public String toString() {
        return className;
    }
}
