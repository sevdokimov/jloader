package com.ess.jloader.packer;

import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public abstract class AttributeFactory {

    @Nullable
    public abstract Attribute read(ClassDescriptor descriptor, String name, ByteBuffer buffer);

}
