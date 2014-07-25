package com.ess.jloader.packer;

import com.ess.jloader.packer.consts.*;
import com.ess.jloader.utils.ClassWriterManager;
import com.ess.jloader.utils.HuffmanOutputStream;
import com.ess.jloader.utils.OpenByteOutputStream;
import com.ess.jloader.utils.Utils;
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

    public OpenByteOutputStream plainDataArray;
    public OpenByteOutputStream forCompressionDataArray;

    private int firstUtfIndex;
    private int constCount;

    private Set<String> generatedStr;

    public ClassDescriptor(ClassReader classReader) {
        this.classReader = classReader;
    }

    public void pack(CompressionContext ctx) throws IOException {
        plainDataArray = new OpenByteOutputStream();
        forCompressionDataArray = new OpenByteOutputStream();

        DataOutputStream plainData = new DataOutputStream(plainDataArray);
        DataOutputStream compressed = new DataOutputStream(forCompressionDataArray);

        String className = classReader.getClassName();

        Collection<AbstractConst> consts = Resolver.resolveAll(classReader, true);
        constCount = getConstPoolSize(consts);

        generatedStr = new LinkedHashSet<String>();
        Set<String> packedStr = new LinkedHashSet<String>();
        List<String> notPackedStr = new ArrayList<String>();

        generatedStr.add(className);

        List<ConstClass> constClasses = new ArrayList<ConstClass>();

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
        }

        Collections.sort(notPackedStr);

        ClassWriter classWriter = new ClassWriter(0);
        ClassWriterManager classWriterManager = new ClassWriterManager(classWriter, constCount);

        int utfCount = generatedStr.size() + packedStr.size() + notPackedStr.size();
        firstUtfIndex = constCount - utfCount;

        classWriterManager.goHead(utfCount);

        for (String s : generatedStr) {
            classWriter.newUTF8(s);
        }
        for (String s : packedStr) {
            classWriter.newUTF8(s);
        }
        for (String s : notPackedStr) {
            classWriter.newUTF8(s);
        }

        classWriterManager.goBack();

        classWriter.newClass(className);

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

        plainData.writeShort(packedStr.size());
        HuffmanOutputStream<String> h = ctx.getLiteralsCache().createHuffmanOutput();
        h.reset(plainData);
        for (String s : packedStr) {
            h.write(s);
        }
        h.finish();

        // First const is class of current class
        if (buffer.get() != 7) throw new RuntimeException();
        if ((buffer.getShort() & 0xFFFF) != firstUtfIndex) throw new RuntimeException();
        //remainingConstCount--;

        copyConstTableTail(buffer, constCount - 1 - 1 - utfCount, compressed);

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

    public void writeTo(OutputStream out, byte[] dictionary) throws IOException {
        plainDataArray.writeTo(out);

        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setDictionary(dictionary);

        DeflaterOutputStream defOut = new DeflaterOutputStream(out, deflater);
        forCompressionDataArray.writeTo(defOut);
        defOut.close();
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

    private void copyUtfIndex(ByteBuffer buffer, DataOutputStream out) throws IOException {
        int index = buffer.getShort() & 0xFFFF;
        assert index >= firstUtfIndex;

        if (constCount - firstUtfIndex > 255) {
            out.writeShort(index - firstUtfIndex);
        }
        else {
            out.writeByte(index - firstUtfIndex);
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
}
