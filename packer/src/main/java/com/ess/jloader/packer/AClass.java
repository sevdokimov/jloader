package com.ess.jloader.packer;

import com.ess.jloader.packer.consts.Const;
import com.ess.jloader.packer.consts.ConstClass;
import com.ess.jloader.packer.consts.ConstFactory;
import com.ess.jloader.packer.consts.ConstUTF8;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class AClass {

    private static final Logger log = Logger.getLogger(AClass.class);

    private final byte[] code;

    private int version;

    private int accessFlags;

    private int thisClassIndex;

    private int superClassIndex;

    private final List<Const> consts = new ArrayList<Const>();

    private final int[] interfaces;

    private FiledInfo[] fields;
    private MethodInfo[] methods;

    private List<AttrInfo> attrs;

    public AClass(byte[] code) throws IOException {
        this.code = code;

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(code));
        if (in.readInt() != 0xCAFEBABE) throw new InvalidClassException();

        version = in.readInt();

        int constPoolSize = in.readUnsignedShort();
        consts.add(null);

        while (consts.size() < constPoolSize) {
            Const c = ConstFactory.readConst(in);
            if (c.isGet2ElementsInPool()) {
                consts.add(null);
            }
            consts.add(c);
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

    @Nullable
    public <T extends Const> T getConst(int index, Class<T> constClass) {
        if (index < 1 || index >= consts.size()) {
            return null;
        }

        Const aConst = consts.get(index);
        if (aConst == null || !constClass.isAssignableFrom(aConst.getClass())) return null;

        return (T) aConst;
    }

    public byte[] getCode() {
        return code;
    }

    public List<Const> getConsts() {
        return consts;
    }

    public static AClass createFromCode(InputStream in) throws IOException {
        byte[] code = IOUtils.toByteArray(in);
        return new AClass(code);
    }

    public void store(OutputStream out) throws IOException {
        out.write(code);
    }

    @Nullable
    public String getName() {
        ConstClass thisClass = getConst(thisClassIndex, ConstClass.class);
        if (thisClass != null) {
            ConstUTF8 aConst = getConst(thisClass.getTypeIndex(), ConstUTF8.class);
            if (aConst != null) {
                return aConst.getText();
            }
        }

        return null;
    }

    @Override
    public String toString() {
        String name = getName();
        return name == null ? "Undeterminated" : name;
    }
}
