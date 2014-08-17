package com.ess.jloader.packer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class AttributeUtils {

//    private static final Field attributeValueField;
//
//    static {
//        try {
//            attributeValueField = org.objectweb.asm.Attribute.class.getDeclaredField("value");
//            attributeValueField.setAccessible(true);
//        } catch (NoSuchFieldException e) {
//            throw Throwables.propagate(e);
//        }
//    }

    private AttributeUtils() {

    }

//    @NotNull
//    public static Attribute convert(AttributeType type, ClassDescriptor descriptor, org.objectweb.asm.Attribute attr) {
//        byte[] data;
//        try {
//            data = (byte[]) attributeValueField.get(attr);
//        } catch (IllegalAccessException e) {
//            throw Throwables.propagate(e);
//        }
//
//        ByteBuffer buffer = ByteBuffer.wrap(data);
//
//        for (AttributeFactory factory : list) {
//            Attribute res = factory.read(type, descriptor, attr.type, buffer);
//            if (res != null) {
//                return res;
//            }
//        }
//
//        return UnknownAttribute.FACTORY.read(type, descriptor, attr.type, buffer);
//
//    }

    @Nullable
    public static Attribute findAttributeByName(Collection<? extends Attribute> attributes, @NotNull String name) {
        for (Attribute attribute : attributes) {
            if (attribute.getName().equals(name)) {
                return attribute;
            }
        }

        return null;
    }

    @Nullable
    public static Attribute removeAttributeByName(Collection<? extends Attribute> attributes, @NotNull String name) {
        for (Attribute attribute : attributes) {
            if (attribute.getName().equals(name)) {
                attributes.remove(attribute);
                return attribute;
            }
        }

        return null;
    }

    public static int extractKnownAttributes(List<Attribute> attributes, List<Attribute> knownAttributes, String ... names) {
        int attrInfo = 0;

        for (int i = 0; i < names.length; i++) {
            Attribute attribute = AttributeUtils.removeAttributeByName(attributes, names[i]);
            if (attribute != null) {
                attrInfo |= 1 << i;
                knownAttributes.add(attribute);
            }
        }

        attrInfo |= attributes.size() << names.length;

        return attrInfo;
    }

    public static ArrayList<Attribute> readAllAttributes(AttributeFactory factory, ClassDescriptor descriptor, ByteBuffer buffer) {
        int attrCount = buffer.getShort() & 0xFFFF;

        ArrayList<Attribute> res = new ArrayList<Attribute>(attrCount);

        for (int i = 0; i < attrCount; i++) {
            int nameIndex = buffer.getShort() & 0xFFFF;
            String name = descriptor.getUtfByIndex(nameIndex);

            res.add(factory.read(descriptor, name, buffer));
        }

        return res;
    }

}
