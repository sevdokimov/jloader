package com.ess.jloader.packer;

import com.ess.jloader.packer.consts.*;
import com.ess.jloader.utils.ClassWriterManager;
import com.ess.jloader.utils.HuffmanOutputStream;
import com.ess.jloader.utils.OpenByteOutputStream;
import com.ess.jloader.utils.Utils;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.log4j.Logger;
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
    private int constCount;

    private final Set<String> generatedStr;

    private List<String> allUtf;

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

    public void pack(CompressionContext ctx) throws IOException {
        plainDataArray = new OpenByteOutputStream();
        forCompressionDataArray = new OpenByteOutputStream();

        DataOutputStream plainData = new DataOutputStream(plainDataArray);
        DataOutputStream compressed = new DataOutputStream(forCompressionDataArray);

        Set<String> packedStr = new LinkedHashSet<String>();
        List<String> notPackedStr = new ArrayList<String>();

        List<ConstClass> constClasses = new ArrayList<ConstClass>();
        List<ConstNameAndType> constNameAndType = new ArrayList<ConstNameAndType>();

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

        ClassWriter classWriter = new ClassWriter(0);
        ClassWriterManager classWriterManager = new ClassWriterManager(classWriter, constCount);

        allUtf = Lists.newArrayList(Iterables.concat(generatedStr, packedStr, notPackedStr));

        int utfCount = allUtf.size();
        firstUtfIndex = constCount - utfCount;

        classWriterManager.goHead(utfCount);

        for (String s : allUtf) {
            classWriter.newUTF8(s);
        }

        classWriterManager.goHead(constNameAndType.size());
        for (ConstNameAndType nameAndType : constNameAndType) {
            nameAndType.toWriter(classWriter);
        }

        classWriterManager.goBack();

        for (ConstClass aClass : constClasses) {
            aClass.toWriter(classWriter);
        }

        classReader.accept(classWriter, 0);

        classWriterManager.finish();

        byte[] classBytes = classWriter.toByteArray();
        ByteBuffer buffer = ByteBuffer.wrap(classBytes);

        int flags = 0;

        int version = buffer.getInt(4);
        flags |= ctx.getVersionCache().getVersionIndex(version);

        if (classBytes.length > 0xFFFF) {
            flags |= Utils.F_LONG_CLASS;
        }

        buffer.position(4); // skip 0xCAFEBABE

        buffer.getInt(); // skip version

        if ((buffer.getShort() & 0xFFFF) != constCount) {
            throw new RuntimeException();
        }

        plainData.writeInt(flags);

        if (classBytes.length > 0xFFFF) {
            plainData.writeInt(classBytes.length);
        }
        else {
            plainData.writeShort(classBytes.length);
        }

        plainData.writeShort(constCount);
        plainData.writeShort(utfCount);

        plainData.writeShort(constClasses.size());

        skipClassConst(buffer, className);

        // First const is class of current class
        for (int i = 1; i < constClasses.size(); i++) {
            if (buffer.get() != 7) throw new RuntimeException();
            int utfIndex = buffer.getShort() & 0xFFFF;

            assert allUtf.get(utfIndex - firstUtfIndex).equals(constClasses.get(i).getType());
            writeUtfIndex(plainData, utfIndex);
        }

        plainData.writeShort(packedStr.size());
        HuffmanOutputStream<String> h = ctx.getLiteralsCache().createHuffmanOutput();
        h.reset(plainData);
        for (String s : packedStr) {
            h.write(s);
        }
        h.finish();

        copyConstTableTail(buffer, constCount - 1 - constClasses.size() - utfCount, compressed);

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

        compressed.write(classBytes, buffer.position(), classBytes.length - buffer.position());
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
        plainDataArray.writeTo(out);

        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setDictionary(dictionary);

        DeflaterOutputStream defOut = new DeflaterOutputStream(out, deflater);
        forCompressionDataArray.writeTo(defOut);
        defOut.close();
    }

    private void skipClassConst(ByteBuffer buffer, String className) {
        int tag = buffer.get();
        if (tag != ConstClass.TAG) throw new RuntimeException("" + tag);

        int utfIndex = buffer.getShort();
        assert className.equals(allUtf.get(utfIndex - firstUtfIndex));
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

    private void writeUtfIndex(DataOutputStream out, int utfIndex) throws IOException {
        assert utfIndex >= firstUtfIndex;
        assert utfIndex < constCount;

        if (constCount - firstUtfIndex > 255) {
            out.writeShort(utfIndex - firstUtfIndex);
        }
        else {
            out.writeByte(utfIndex - firstUtfIndex);
        }
    }

    private void copyUtfIndex(ByteBuffer buffer, DataOutputStream out) throws IOException {
        writeUtfIndex(out, buffer.getShort() & 0xFFFF);
    }

    private void copyConstTableTail(ByteBuffer buffer, int constCount, DataOutputStream out) throws IOException {
        for (int i = 0; i < constCount; i++) {
            int tag = buffer.get();

            out.write(tag);

            switch (tag) {
                case 9: // ClassWriter.FIELD:
                case 10: // ClassWriter.METH:
                case 11: // ClassWriter.IMETH:
//                    int classIndex = buffer.getShort();
//                    out.writeShort(classIndex);
//                    int nameType = buffer.getShort();
//                    out.writeShort(nameType);
//                    break;

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
