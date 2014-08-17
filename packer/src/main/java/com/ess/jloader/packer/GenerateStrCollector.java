package com.ess.jloader.packer;

import com.ess.jloader.packer.consts.AbstractConst;
import com.ess.jloader.packer.consts.ConstUtf;
import com.ess.jloader.utils.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * @author Sergey Evdokimov
 */
public class GenerateStrCollector {

    private static Attribute findAttribute(@Nullable List<Attribute> attributes, @NotNull String name) {
        if (attributes == null) return null;

        for (Attribute attribute : attributes) {
            if (name.equals(attribute.type)) {
                return attribute;
            }
        }

        return null;
    }

    public static Set<String> collectGeneratedStr(ClassNode classNode, Collection<AbstractConst> consts) {
        Set<String> res = new LinkedHashSet<String>();

        res.add(classNode.name);

        Set<String> allUtfs = new HashSet<String>();
        for (AbstractConst aConst : consts) {
            if (aConst instanceof ConstUtf) {
                allUtfs.add(((ConstUtf) aConst).getValue());
            }
        }

        for (String s : Utils.COMMON_UTF) {
            if (allUtfs.contains(s)) {
                res.add(s);
            }
        }

//        if (classNode.superName.equals("java/lang/Object")) {
//            res.add("java/lang/Object");
//        }

        for (FieldNode field : classNode.fields) {
            if (field.signature != null) {
                res.add("Signature");
            }

            if (field.value != null) {
                res.add("ConstantValue");
            }
        }

        for (MethodNode method : classNode.methods) {
            if (!Modifier.isNative(method.access) && !Modifier.isAbstract(method.access)) {
                res.add("Code");
            }

            if (method.signature != null) {
                res.add("Signature");
            }

            if (method.exceptions.size() > 0) {
                res.add("Exceptions");
            }

            if (hasLineNumbers(method)) {
                res.add("LineNumberTable");
            }
        }

        if (classNode.sourceFile != null) {
            res.add("SourceFile");

            String sourceFileName = Utils.generateSourceFileName(classNode.name);
            if (classNode.sourceFile.equals(sourceFileName)) {
                res.add(sourceFileName);
            }
        }

        if (classNode.innerClasses.size() > 0) {
            res.add("InnerClasses");

            int anonymousClassCount = PackUtils.evaluateAnonymousClassCount(classNode);

            for (int i = 1; i <= anonymousClassCount; i++) {
                res.add(classNode.name + '$' + i);
            }
        }

        if (classNode.signature != null) {
            res.add("Signature");
        }

        return res;
    }

    private static boolean hasLineNumbers(MethodNode method) {
        for (Iterator<AbstractInsnNode> itr = method.instructions.iterator(); itr.hasNext(); ) {
            if (itr.next() instanceof LineNumberNode) {
                return true;
            }
        }

        return false;
    }
}
