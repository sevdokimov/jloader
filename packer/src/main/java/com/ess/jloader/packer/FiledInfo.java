package com.ess.jloader.packer;

import com.ess.jloader.packer.attribute.AttributeParser;
import com.ess.jloader.packer.attribute.ByteArrayAttributeParser;
import com.ess.jloader.packer.attribute.ConstantValueAttrParser;
import com.ess.jloader.packer.attribute.MarkerAttribute;
import com.ess.jloader.packer.consts.CRef;
import com.ess.jloader.packer.consts.ConstUft;

import java.io.DataInput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.ess.jloader.utils.Modifier .*;

/**
 * @author Sergey Evdokimov
 */
public class FiledInfo {

    public static final int MODIFIER_MASK = PUBLIC + PRIVATE + PROTECTED + STATIC + FINAL + VOLATILE
        + TRANSIENT
        + SYNTHETIC + ENUM;

    private static final Map<String, AttributeParser> ATTR_PERSERS = new HashMap<String, AttributeParser>();
    static {
        ATTR_PERSERS.put("Synthetic", MarkerAttribute.INSTANCE);
        ATTR_PERSERS.put("Deprecated", MarkerAttribute.INSTANCE);
        ATTR_PERSERS.put("ConstantValue", ConstantValueAttrParser.INSTANCE);

        ATTR_PERSERS.put("RuntimeInvisibleAnnotations", ByteArrayAttributeParser.INSTANCE);
        ATTR_PERSERS.put("RuntimeVisibleAnnotations", ByteArrayAttributeParser.INSTANCE);

        ATTR_PERSERS.put("Signature", ByteArrayAttributeParser.INSTANCE);
    }

    private int accessFlag;

    private CRef<ConstUft> name;
    private CRef<ConstUft> descriptor;

    public Map<String, Object> attrs;

    private final AClass aClass;

    public FiledInfo(AClass aClass, DataInput in) throws IOException {
        this.aClass = aClass;

        accessFlag = in.readUnsignedShort();

        name = aClass.createRef(ConstUft.class, in);
        descriptor = aClass.createRef(ConstUft.class, in);
        attrs = PackUtils.readAttrs(aClass, in, ATTR_PERSERS);

        if ((accessFlag & ~MODIFIER_MASK) != 0) throw new InvalidClassException();
        if ((accessFlag & (STATIC | TRANSIENT)) == (STATIC | TRANSIENT)) throw new InvalidClassException();
    }

    public int getAccessFlag() {
        return accessFlag;
    }

    public CRef<ConstUft> getName() {
        return name;
    }

    public CRef<ConstUft> getDescriptor() {
        return descriptor;
    }

    public AClass getaClass() {
        return aClass;
    }

    public Map<String, Object> getAttrs() {
        return attrs;
    }
}
