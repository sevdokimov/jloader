package com.ess.jloader.packer;

import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public class AttributeFactories {

    private final AttributeFactory[] list = new AttributeFactory[]{};

    private static final AttributeFactories INSTANCE = new AttributeFactories();

    private AttributeFactories() {

    }

    public static AttributeFactories getInstance() {
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
}
