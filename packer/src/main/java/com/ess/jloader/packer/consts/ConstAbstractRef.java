package com.ess.jloader.packer.consts;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;

/**
 * @author Sergey Evdokimov
 */
public abstract class ConstAbstractRef extends AbstractConst {

    private final Type owner;
    private final String name;
    private final String descr;

    ConstAbstractRef(int tag, Resolver resolver, int pos) {
        super(tag);

        int clsIndex = resolver.getClassReader().readUnsignedShort(pos);
        owner = resolver.resolve(clsIndex, ConstClass.class).getValue();

        ConstNameAndType nameAndType = resolver.resolve(resolver.getClassReader().readUnsignedShort(pos + 2), ConstNameAndType.class);
        name = nameAndType.getName();
        descr = nameAndType.getDescr();
    }

    public ConstAbstractRef(int tag, @NotNull String owner, @NotNull String name, @NotNull String descr) {
        super(tag);
        this.owner = Type.getObjectType(owner);
        this.name = name;
        this.descr = descr;
    }

    public Type getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getDescr() {
        return descr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConstAbstractRef that = (ConstAbstractRef) o;

        if (!owner.equals(that.owner)) return false;
        if (!descr.equals(that.descr)) return false;
        if (!name.equals(that.name)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = owner.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + descr.hashCode();
        return result;
    }
}
