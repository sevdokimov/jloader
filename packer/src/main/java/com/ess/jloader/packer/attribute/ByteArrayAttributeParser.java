package com.ess.jloader.packer.attribute;

import com.ess.jloader.packer.AClass;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ByteArrayAttributeParser implements AttributeParser {

    public static final ByteArrayAttributeParser INSTANCE = new ByteArrayAttributeParser();

    @NotNull
    @Override
    public Object parse(AClass aClass, int length, DataInput in) throws IOException {
        byte[] res = new byte[length];
        in.readFully(res);
        return res;
    }
}
