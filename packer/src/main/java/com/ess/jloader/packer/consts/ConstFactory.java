package com.ess.jloader.packer.consts;

import com.ess.jloader.packer.InvalidClassException;

import java.io.DataInput;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * @author Sergey Evdokimov
 */
@SuppressWarnings({"unchecked"})
public class ConstFactory {

    // See http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#1348
    private static final Class<? extends Const>[] CLASS_MAP = new Class[]{
        null,
        ConstUTF8.class,             // 1
        null,
        ConstInt.class,              // 3
        ConstFloat.class,            // 4
        ConstLong.class,             // 5
        ConstDouble.class,           // 6
        ConstClass.class,            // 7
        ConstString.class,           // 8
        ConstFiled.class,            // 9
        ConstMethod.class,           // 10
        ConstInterfaceMethod.class,  // 11
        ConstNameAndType.class,      // 12
    };

    public static Const readConst(DataInput in) throws IOException {
        int code = in.readUnsignedByte();
        if (code < 0 || code >= CLASS_MAP.length) throw new InvalidClassException();

        Class<? extends Const> aClass = CLASS_MAP[code];
        if (aClass == null) throw new InvalidClassException();

        try {
            Constructor<? extends Const> constructor = aClass.getConstructor(DataInput.class);
            return constructor.newInstance(in);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException)e.getCause();
            }
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
