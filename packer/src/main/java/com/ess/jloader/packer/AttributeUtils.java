package com.ess.jloader.packer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Sergey Evdokimov
 */
public class AttributeUtils {

    private final AttributeFactory[] list = new AttributeFactory[]{AttributeCode.FACTORY};

    private static final AttributeUtils INSTANCE = new AttributeUtils();

    private AttributeUtils() {

    }

    public static AttributeUtils getInstance() {
        return INSTANCE;
    }

    @NotNull
    public Attribute read(AttributeType type, ClassDescriptor descriptor, String name, ByteBuffer buffer) {
        for (AttributeFactory factory : list) {
            Attribute res = factory.read(type, descriptor, name, buffer);
            if (res != null) {
                return res;
            }
        }

        return UnknownAttribute.FACTORY.read(type, descriptor, name, buffer);
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

    public static ArrayList<Attribute> readAllAttributes(AttributeType type, ClassDescriptor descriptor, ByteBuffer buffer) {
        int attrCount = buffer.getShort() & 0xFFFF;

        ArrayList<Attribute> res = new ArrayList<Attribute>();

        for (int i = 0; i < attrCount; i++) {
            int nameIndex = buffer.getShort() & 0xFFFF;
            String name = descriptor.getUtfByIndex(nameIndex);

            res.add(AttributeUtils.getInstance().read(type, descriptor, name, buffer));
        }

        return res;
    }

}
