package com.ess.jloader.packer;

import com.ess.jloader.packer.consts.Const;
import com.ess.jloader.packer.consts.ConstFactory;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class AClass {

    private final byte[] code;

    private int version;

    private int accessFlags;

    private int thisClassIndex;

    private int superClassIndex;

    private final List<Const> consts = new ArrayList<Const>();

    private final int[] interfaces;

    private FiledInfo[] fields;
    private MethodInfo[] methods;

    private AttrInfo[] attrs;

    public AClass(byte[] code) throws InvalidClassException, IOException {
        this.code = code;

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(code));
        if (in.readInt() != 0xCAFEBABE) throw new InvalidClassException();

        version = in.readInt();

        int constPoolSize = in.readUnsignedShort();
        consts.add(null);

        for (int i = 1; i < constPoolSize; i++) {
            consts.add(ConstFactory.readConst(in));
        }

        accessFlags = in.readUnsignedShort();

        thisClassIndex = in.readUnsignedShort();
        superClassIndex = in.readUnsignedShort();

        int interfaceCount = in.readUnsignedShort();

        interfaces = new int[interfaceCount];
        for (int i = 0; i < interfaceCount; i++) {
            interfaces[i] = in.readUnsignedShort();
        }

        int fieldCount = in.readUnsignedShort();
        fields = new FiledInfo[fieldCount];
        for (int i = 0; i < fieldCount; i++) {
            fields[i] = new FiledInfo(in);
        }

        int methodCount = in.readUnsignedShort();
        methods = new MethodInfo[methodCount];
        for (int i = 0; i < methodCount; i++) {
            methods[i] = new MethodInfo(in);
        }

        attrs = AttrInfo.readAttrs(in);

        assert in.read() == -1;
    }

    public byte[] getCode() {
        return code;
    }

    public static AClass createFromCode(InputStream in) throws IOException, InvalidClassException {
        byte[] code = IOUtils.toByteArray(in);
        return new AClass(code);
    }

    public void store(OutputStream out) throws IOException {
        out.write(code);
    }
}
