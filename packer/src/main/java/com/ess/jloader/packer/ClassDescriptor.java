package com.ess.jloader.packer;

import com.ess.jloader.packer.consts.*;
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

        Set<String> generatedStr = new LinkedHashSet<String>();
        Set<String> packedStr = new LinkedHashSet<String>();
        List<String> notPackedStr = new ArrayList<String>();

        generatedStr.add(className);

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
        }

        Collections.sort(notPackedStr);

        ClassWriter classWriter = new ClassWriter(0);
        classWriter.newClass(className);
        for (String s : packedStr) {
            classWriter.newUTF8(s);
        }

        for (String s : notPackedStr) {
            classWriter.newUTF8(s);
        }

        classReader.accept(classWriter, 0);

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

        int constCount = buffer.getShort() & 0xFFFF;
        int remainingConstCount = constCount - 1;

        assert constCount == getConstPoolSize(consts);

        plainData.writeInt(flags);

        if (classBytes.length > 0xFFFF) {
            plainData.writeInt(classBytes.length);
        }
        else {
            plainData.writeShort(classBytes.length);
        }

        plainData.writeShort(constCount);

        plainData.writeShort(packedStr.size());
        HuffmanOutputStream<String> h = ctx.getLiteralsCache().createHuffmanOutput();
        h.reset(plainData);
        for (String s : packedStr) {
            h.write(s);
        }
        h.finish();

        // First const is class name
        skipUtfConst(buffer, className);
        remainingConstCount--;

        if (buffer.get() != 7) throw new RuntimeException();
        if (buffer.getShort() != 1) throw new RuntimeException();
        remainingConstCount--;

        for (String s : packedStr) {
            skipUtfConst(buffer, s);
            remainingConstCount--;
        }

        copyConstTableTail(buffer, remainingConstCount, compressed);

        int accessFlags = buffer.getShort();
        compressed.writeShort(accessFlags);

        int thisClassIndex = buffer.getShort();
        if (thisClassIndex != 2) throw new RuntimeException(String.valueOf(thisClassIndex));

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

        int strSize = buffer.getShort();
        buffer.position(buffer.position() + strSize);
    }

    private void copyConstTableTail(ByteBuffer buffer, int constCount, DataOutputStream out) throws IOException {
        int oldPosition = buffer.position();

        for (int i = 0; i < constCount; i++) {
            int tag = buffer.get();

            int size;
            switch (tag) {
                case 9: // ClassWriter.FIELD:
                case 10: // ClassWriter.METH:
                case 11: // ClassWriter.IMETH:
                case 3: // ClassWriter.INT:
                case 4: // ClassWriter.FLOAT:
                case 12: // ClassWriter.NAME_TYPE:
                case 18: // ClassWriter.INDY:
                    size = 4;
                    break;
                case 5:// ClassWriter.LONG:
                case 6: // ClassWriter.DOUBLE:
                    size = 8;
                    ++i;
                    break;
                case 1: // ClassWriter.UTF8:
                    size = buffer.getShort() & 0xFFFF;
                    break;

                case 15: // ClassWriter.HANDLE:
                    size = 3;
                    break;
                // case ClassWriter.CLASS:
                // case ClassWriter.STR:
                // case ClassWriter.MTYPE
                default:
                    size = 2;
                    break;
            }

            buffer.position(buffer.position() + size);
        }

        out.write(buffer.array(), oldPosition, buffer.position() - oldPosition);
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
