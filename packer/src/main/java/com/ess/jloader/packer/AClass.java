package com.ess.jloader.packer;

import com.ess.jloader.packer.attribute.*;
import com.ess.jloader.packer.consts.*;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;

/**
 * @author Sergey Evdokimov
 */
@SuppressWarnings({"unchecked"})
public class AClass {

    private static final Logger log = Logger.getLogger(AClass.class);

    private static final Map<String, AttributeParser> ATTR_PERSERS = new HashMap<String, AttributeParser>();
    static {
        ATTR_PERSERS.put("Synthetic", MarkerAttribute.INSTANCE);
        ATTR_PERSERS.put("Deprecated", MarkerAttribute.INSTANCE);
        ATTR_PERSERS.put("SourceFile", new SourceFileParser());

        ATTR_PERSERS.put("InnerClasses", ByteArrayAttributeParser.INSTANCE);
        ATTR_PERSERS.put("EnclosingMethod", ByteArrayAttributeParser.INSTANCE);

        ATTR_PERSERS.put("RuntimeInvisibleAnnotations", ByteArrayAttributeParser.INSTANCE);
        ATTR_PERSERS.put("RuntimeVisibleAnnotations", ByteArrayAttributeParser.INSTANCE);

        ATTR_PERSERS.put("Signature", ByteArrayAttributeParser.INSTANCE);
    }

    private final byte[] code;

    private int version;

    private int accessFlags;

    private CRef<ConstClass> thisClass;

    private CRef<ConstClass> superClass;

    private final List<Const> consts = new ArrayList<Const>();

    private final CRef<ConstClass>[] interfaces;

    private FiledInfo[] fields;
    private MethodInfo[] methods;

    public Map<String, Object> attrs;

    private List<CRef<?>> unresolvedRefs = new ArrayList<CRef<?>>();

    public AClass(byte[] code) throws IOException {
        this.code = code;

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(code));
        if (in.readInt() != 0xCAFEBABE) throw new InvalidClassException();

        version = in.readInt();

        int constPoolSize = in.readUnsignedShort();
        consts.add(null);

        while (consts.size() < constPoolSize) {
            Const c = ConstFactory.readConst(this, in);
            consts.add(c);
            if (c.isGet2ElementsInPool()) {
                consts.add(null);
            }
        }

        for (CRef<?> ref : unresolvedRefs) {
            ref.resolve(this);
        }
        unresolvedRefs = null;

        accessFlags = in.readUnsignedShort();

        thisClass = createRef(ConstClass.class, in);
        int superClassIndex = in.readUnsignedShort();
        if (superClassIndex != 0) {
            superClass = createRef(ConstClass.class, superClassIndex);
        }

        int interfaceCount = in.readUnsignedShort();

        interfaces = new CRef[interfaceCount];
        for (int i = 0; i < interfaceCount; i++) {
            interfaces[i] = createRef(ConstClass.class, in);
        }

        int fieldCount = in.readUnsignedShort();
        fields = new FiledInfo[fieldCount];
        for (int i = 0; i < fieldCount; i++) {
            fields[i] = new FiledInfo(this, in);
        }

        int methodCount = in.readUnsignedShort();
        methods = new MethodInfo[methodCount];
        for (int i = 0; i < methodCount; i++) {
            methods[i] = new MethodInfo(this, in);
        }

        attrs = PackUtils.readAttrs(this, in, ATTR_PERSERS);

        assert in.read() == -1;
    }

    public Map<String, Object> getAttrs() {
        return attrs;
    }

    public int getJavaVersion() {
        return version;
    }

    @Nullable
    public Const getConst(int index) {
        if (index < 1 || index >= consts.size()) {
            return null;
        }

        return consts.get(index);
    }

    @Nullable
    public <T extends Const> T getConst(int index, Class<T> constClass) {
        Const aConst = getConst(index);
        if (aConst == null || !constClass.isAssignableFrom(aConst.getClass())) return null;

        return (T) aConst;
    }

    public byte[] getCode() {
        return code;
    }

    public List<? extends Const> getConsts() {
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
        if (unresolvedRefs != null) {
            return null;
        }

        return thisClass.get().getName().get().getText().replace('/', '.');
    }

    @NotNull
    public <T extends Const> CRef<T> createRef(Class<T> constClazz, int index) throws InvalidClassException {
        CRef<T> ref = new CRef<T>(constClazz, index);

        if (unresolvedRefs != null) {
            unresolvedRefs.add(ref);
        }
        else {
            ref.resolve(this);
        }

        return ref;
    }

    @NotNull
    public <T extends Const> CRef<T> createRef(Class<T> constClazz, DataInput in) throws IOException {
        int index = in.readUnsignedShort();
        return createRef(constClazz, index);
    }

    @Override
    public String toString() {
        String name = getName();
        return name == null ? "Undeterminated" : name;
    }
}
