package com.ess.jloader.packer.attribute;

import com.ess.jloader.packer.AClass;
import com.ess.jloader.packer.InvalidClassException;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public interface AttributeParser<T> {

    @NotNull
    public T parse(AClass aClass, int length, DataInput in) throws IOException;

}
