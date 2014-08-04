package com.ess.jloader.packer;

import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public abstract class AttributeFactory<T extends Attribute> {

    @Nullable
    public abstract T read(AttributeType type, ClassDescriptor descriptor, String name, ByteBuffer buffer);

}
