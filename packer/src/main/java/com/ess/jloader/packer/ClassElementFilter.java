package com.ess.jloader.packer;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.commons.EmptyVisitor;

/**
 * @author Sergey Evdokimov
 */
public class ClassElementFilter extends ClassAdapter {

    private final Config cfg;

    public ClassElementFilter(ClassVisitor cv, Config cfg) {
        super(cv);
        this.cfg = cfg;
    }

    @Override
    public void visitSource(String source, String debug) {
        super.visitSource(source, cfg.isRemoveSourceDebugExtensionAttribute() ? null : debug);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (visible || !cfg.isRemoveInvisibleAnnotation()) {
            return super.visitAnnotation(desc, visible);
        }

        return PackUtils.EMPTY_VISITOR;
    }


}
