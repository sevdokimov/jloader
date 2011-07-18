package com.ess.jloader.packer.code;

import com.ess.jloader.packer.AClass;
import com.ess.jloader.packer.InvalidClassException;
import com.ess.jloader.packer.attribute.AttributeParser;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class CodeAttributeParser implements AttributeParser<Code> {

    @NotNull
    @Override
    public Code parse(AClass aClass, int length, DataInput in) throws IOException {
        in.skipBytes(length);
        return new Code();
    }
}
