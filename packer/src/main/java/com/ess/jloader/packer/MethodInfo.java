package com.ess.jloader.packer;

import com.ess.jloader.packer.attribute.AnnotationsAttrParser;
import com.ess.jloader.packer.attribute.AttributeParser;
import com.ess.jloader.packer.attribute.MarkerAttribute;
import com.ess.jloader.packer.attribute.ByteArrayAttributeParser;
import com.ess.jloader.packer.code.CodeAttributeParser;
import com.ess.jloader.packer.consts.CRef;
import com.ess.jloader.packer.consts.ConstUft;

import java.io.DataInput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class MethodInfo {

    private static final Map<String, AttributeParser> ATTR_PERSERS = new HashMap<String, AttributeParser>();
    static {
        ATTR_PERSERS.put("Synthetic", MarkerAttribute.INSTANCE);
        ATTR_PERSERS.put("Deprecated", MarkerAttribute.INSTANCE);

        ATTR_PERSERS.put("Exceptions", ByteArrayAttributeParser.INSTANCE);
        ATTR_PERSERS.put("Code", new CodeAttributeParser());

        ATTR_PERSERS.put("RuntimeInvisibleAnnotations", ByteArrayAttributeParser.INSTANCE);
        ATTR_PERSERS.put("RuntimeInvisibleParameterAnnotations", ByteArrayAttributeParser.INSTANCE);

        ATTR_PERSERS.put("RuntimeVisibleAnnotations", new AnnotationsAttrParser());

        ATTR_PERSERS.put("Signature", ByteArrayAttributeParser.INSTANCE);
    }

    private int accessFlag;
    private CRef<ConstUft> name;
    private CRef<ConstUft> descriptor;
    public Map<String, Object> attrs;

    public MethodInfo(AClass aClass, DataInput in) throws IOException {
        accessFlag = in.readUnsignedShort();
        name = aClass.createRef(ConstUft.class, in);
        descriptor = aClass.createRef(ConstUft.class, in);
        attrs = PackUtils.readAttrs(aClass, in, ATTR_PERSERS);
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

    public Map<String, Object> getAttrs() {
        return attrs;
    }
}
