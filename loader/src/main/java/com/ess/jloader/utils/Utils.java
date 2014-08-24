package com.ess.jloader.utils;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;

/**
 * @author Sergey Evdokimov
 */
public class Utils {

    public static final boolean CHECK_LIMITS = false;

    public static final String[] COMMON_UTF = new String[] {

    };

    public static final String[] PREDEFINED_UTF = {
            "Code",
            "Exceptions",
            "Signature",
            "ConstantValue",
            "LineNumberTable",
            "LocalVariableTable",
            "this",
            "LocalVariableTypeTable",
            "StackMapTable",
    };


    public static final int PS_CODE = 0;
    public static final int PS_EXCEPTIONS = 1;
    public static final int PS_SIGNATURE = 2;
    public static final int PS_CONST_VALUE = 3;
    public static final int PS_LINE_NUMBER_TABLE = 4;
    public static final int PS_LOCAL_VARIABLE_TABLE = 5;
    public static final int PS_THIS = 6;
    public static final int PS_LOCAL_VARIABLE_TYPE_TABLE = 7;
    public static final int PS_STACK_MAP_TABLE = 8;

    public static final byte[] PREDEFINED_UTF_BYTES;
    public static final int[] PREDEFINED_UTF_BYTE_INDEXES;

    public static final byte[] C_SourceFile = toByteArray("SourceFile");
    public static final byte[] C_InnerClasses = toByteArray("InnerClasses");
    public static final byte[] C_EnclosingMethod = toByteArray("EnclosingMethod");

    static {
        try {
            // Init predefined bytes
            PREDEFINED_UTF_BYTE_INDEXES = new int[PREDEFINED_UTF.length + 1];

            int bytesCount = 0;
            for (int i = 0; i < PREDEFINED_UTF.length; i++) {
                PREDEFINED_UTF_BYTE_INDEXES[i] = bytesCount;

                bytesCount += 2 + PREDEFINED_UTF[i].length();
            }

            PREDEFINED_UTF_BYTE_INDEXES[PREDEFINED_UTF.length] = bytesCount;

            PREDEFINED_UTF_BYTES = new byte[bytesCount];
            DataOutputStream out = new DataOutputStream(new OpenByteOutputStream(PREDEFINED_UTF_BYTES));
            for (String s : PREDEFINED_UTF) {
                out.writeUTF(s);
            }

            assert out.size() == bytesCount;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static final byte PACKER_VERSION = 0x01;

    public static final byte MAGIC = (byte) 0xAA;

    public static String fileNameToClassName(String fileName) {
        assert fileName.endsWith(".class") : fileName;
        return fileName.substring(0, fileName.length() - ".class".length());
    }

    public static String generateSourceFileName(String className) {
        className = className.substring(className.lastIndexOf('/') + 1);

        int dollarIndex = className.indexOf('$');
        if (dollarIndex != -1) {
            className = className.substring(0, dollarIndex);
        }

        return className.concat(".java");
    }

    public static byte[] toByteArray(String s) {
        try {
            int size = s.length() + 2;
            OpenByteOutputStream byteOut = new OpenByteOutputStream(size);
            new DataOutputStream(byteOut).writeUTF(s);

            assert byteOut.size() == size;

            return byteOut.getBuffer();
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    public static int readLimitedShort(DataInputStream in, int limit) throws IOException {
        if (CHECK_LIMITS) {
            int storedLimit = in.readUnsignedShort();
            assert storedLimit == limit;
        }

        int res;
        if (limit < 256) {
            if (limit == 0) {
                return 0;
            }
            res = in.readUnsignedByte();
        }
        else if (limit < 256 * 3) {
            res = readSmallShort3(in);
        }
        else {
            res = in.readUnsignedShort();
        }

        assert res <= limit;

        return res;
    }

    public static int readSmallShort3(DataInput in) throws IOException {
        if (CHECK_LIMITS) {
            if (in.readByte() != 0x73) throw new RuntimeException();
        }

        int x = in.readUnsignedByte();
        if (x <= 251) {
            return x;
        }

        if (x == 255) {
            return in.readUnsignedShort();
        }

        return (((x - 251) << 8) + in.readUnsignedByte()) - 4;
    }

    public static String generateEnclosingClassName(String thisClassName) {
        int idx = thisClassName.lastIndexOf('$');
        return idx == -1 ? null : thisClassName.substring(0, idx);
    }

    public static int crc32(byte[] data, int off, int len) {
        CRC32 crc = new CRC32();
        crc.update(data, off, len);
        return (int) crc.getValue();
    }
}
