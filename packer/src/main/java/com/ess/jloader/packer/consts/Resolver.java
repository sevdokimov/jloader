package com.ess.jloader.packer.consts;

import com.ess.jloader.packer.InvalidJarException;
import com.ess.jloader.packer.PackUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Sergey Evdokimov
 */
public class Resolver {

    private static final Object IN_PROCESS_MARKER = new Object();

    private Object[] buff;

    private ClassReader classReader;

    private char[] charBuff;

    public Resolver(@NotNull ClassReader classReader) {
        this.classReader = classReader;
        buff = new Object[classReader.getItemCount()];
        charBuff = new char[classReader.getMaxStringLength()];
    }

    public ClassReader getClassReader() {
        return classReader;
    }

    public <T extends AbstractConst> T resolve(int index, @Nullable Class<T> expectedClass) {
        if (index == 0) throw new InvalidJarException();

        if (buff[index] == IN_PROCESS_MARKER) throw new InvalidJarException();

        AbstractConst res;
        if (buff[index] != null) {
            res = (AbstractConst) buff[index];
        }
        else {
            buff[index] = IN_PROCESS_MARKER;

            int pos = classReader.getItem(index);
            if (pos == 0) {
                res = null;
            }
            else {
                byte tag = classReader.b[pos - 1];
                switch (tag) {
                    case ConstUtf.TAG:
                        res = new ConstUtf(PackUtils.readUtf(classReader, pos));
                        break;

                    case ConstField.TAG: {
                        res = new ConstField(this, pos);
                        break;
                    }
                    case ConstMethod.TAG: {
                        res = new ConstMethod(this, pos);
                        break;
                    }
                    case ConstInterface.TAG: {
                        res = new ConstInterface(this, pos);
                        break;
                    }

                    case ConstInteger.TAG:
                        res = new ConstInteger(classReader.readInt(pos));
                        break;
                    case ConstFloat.TAG:
                        res = new ConstFloat(Float.intBitsToFloat(classReader.readInt(pos)));
                        break;
                    case ConstLong.TAG:
                        res = new ConstLong(classReader.readLong(pos));
                        break;
                    case ConstDouble.TAG:
                        res = new ConstDouble(Double.longBitsToDouble(classReader.readLong(pos)));
                        break;
                    case ConstString.TAG:
                        res = new ConstString(classReader.readUTF8(pos, charBuff));
                        break;
                    case ConstClass.TAG:
                        res = new ConstClass((org.objectweb.asm.Type) classReader.readConst(index, charBuff));
                        break;

    //                case ConstMethodHandle.TAG:
    //                    res = new ConstMethodHandle(
    //                            classReader.readByte(pos),
    //                            resolve(classReader.readUnsignedShort(pos + 1), ConstUtf.class).getValue(),
    //
    //                    );
    //                    break;
                    case ConstNameAndType.TAG:
                        res = new ConstNameAndType(
                                resolve(classReader.readUnsignedShort(pos), ConstUtf.class).getValue(),
                                resolve(classReader.readUnsignedShort(pos + 2), ConstUtf.class).getValue()
                        );
                        break;

                    case ConstMethodTypeInfo.TAG:
                        res = new ConstMethodTypeInfo(resolve(classReader.readUnsignedShort(pos), ConstUtf.class).getValue());
                        break;

                    default:
                        throw new InvalidJarException(String.valueOf(tag));

                }
            }

            buff[index] = res;
        }

        if (expectedClass != null && res.getClass() != expectedClass) {
            throw new InvalidJarException();
        }

        return (T) res;
    }

    public static Collection<AbstractConst> resolveAll(ClassReader classReader) {
        return resolveAll(classReader, false);
    }

    public static Collection<AbstractConst> resolveAll(ClassReader classReader, boolean filterByRepack) {
        Resolver resolver = new Resolver(classReader);

        Set<AbstractConst> res = new LinkedHashSet<AbstractConst>();

        for (int i = 1; i < classReader.getItemCount(); i++) {
            AbstractConst aConst = resolver.resolve(i, null);
            if (aConst != null) {
                res.add(aConst);
            }
        }

        if (filterByRepack) {
            ClassWriter classWriter = new ClassWriter(0);
            classReader.accept(classWriter, 0);
            res.retainAll(resolveAll(new ClassReader(classWriter.toByteArray())));
        }

        return res;
    }

    public static AbstractConst[] resolveAllPlain(ClassReader classReader) {
        Resolver resolver = new Resolver(classReader);

        AbstractConst[] res = new AbstractConst[classReader.getItemCount()];

        for (int i = 1; i < classReader.getItemCount(); i++) {
            res[i] = resolver.resolve(i, null);
        }

        return res;
    }
}
