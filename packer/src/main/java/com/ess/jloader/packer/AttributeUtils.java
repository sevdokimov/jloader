package com.ess.jloader.packer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;

/**
 * @author Sergey Evdokimov
 */
public class AttributeUtils {

    private final AttributeFactory[] list = new AttributeFactory[]{};

    private static final AttributeUtils INSTANCE = new AttributeUtils();

    private AttributeUtils() {

    }

    public static AttributeUtils getInstance() {
        return INSTANCE;
    }

    @NotNull
    public Attribute read(AttributeType type, String name, ByteBuffer buffer) throws IOException {
        for (AttributeFactory factory : list) {
            Attribute res = factory.read(type, name, buffer);
            if (res != null) {
                return res;
            }
        }

        return UnknownAttribute.FACTORY.read(type, name, buffer);
    }

    @Nullable
    public static Attribute findAttributeByName(Collection<? extends Attribute> attributes, @NotNull String name) {
        for (Attribute attribute : attributes) {
            if (attribute.getName().equals(name)) {
                return attribute;
            }
        }

        return null;
    }
}
