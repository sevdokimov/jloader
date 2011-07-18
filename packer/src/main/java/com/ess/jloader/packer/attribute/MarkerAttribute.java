package com.ess.jloader.packer.attribute;

import com.ess.jloader.packer.AClass;
import com.ess.jloader.packer.InvalidClassException;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;

/**
 * @author Sergey Evdokimov
 */
public class MarkerAttribute implements AttributeParser<Boolean> {

    public static final MarkerAttribute INSTANCE = new MarkerAttribute();

    private MarkerAttribute() {
    }

    @NotNull
    @Override
    public Boolean parse(AClass aClass, int length, DataInput in) throws InvalidClassException {
        if (length != 0) throw new InvalidClassException();

        return true;
    }
}
