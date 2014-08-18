package com.ess.jloader.packer.attributes;

import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public abstract class AttributeFactory {

    @Nullable
    public abstract Attribute read(AttrContext ctx, String name, ByteBuffer buffer);

}
