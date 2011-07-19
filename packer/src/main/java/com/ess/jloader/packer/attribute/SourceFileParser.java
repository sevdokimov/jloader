package com.ess.jloader.packer.attribute;

import com.ess.jloader.packer.AClass;
import com.ess.jloader.packer.consts.CRef;
import com.ess.jloader.packer.consts.ConstUtf;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class SourceFileParser implements AttributeParser<CRef<ConstUtf>> {

    @NotNull
    @Override
    public CRef<ConstUtf> parse(AClass aClass, int length, DataInput in) throws IOException {
        return aClass.createRef(ConstUtf.class, in);
    }
}
