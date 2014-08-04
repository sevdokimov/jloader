package com.ess.jloader.utils;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public class Utils {

    public static final String[] PREDEFINED_UTF = {
            "Code",
            "LineNumberTable",
            "LocalVariableTable",
            "Exceptions",
            "InnerClasses",
            "Synthetic"
    };

    public static final int PS_CODE = 0;

    public static final byte PACKER_VERSION = 0x01;

    public static final byte MAGIC = (byte) 0xAA;

    /*
     * Flag bits:
     * 0, 1, 2 - class version index.
     * 4,5 - interface count
     * 6 - F_HAS_SOURCE_FILE_ATTR
     */


    public static final int F_INTERFACE_COUNT_SHIFT = 4;

    public static final int F_HAS_SOURCE_FILE_ATTR = 1 << 6;

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

    public static void read(DataInputStream in, ByteBuffer buffer, int length) throws IOException {
        int position = buffer.position();
        in.readFully(buffer.array(), position, length);
        buffer.position(position + length);
    }
}
