package com.ess.jloader.packer;

import com.ess.jloader.packer.consts.AbstractConst;
import com.ess.jloader.packer.consts.ConstUtf;
import com.ess.jloader.utils.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
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

    public static Set<String> collectGeneratedStr(ClassReader classReader, Collection<AbstractConst> consts) {
        Set<String> res = new LinkedHashSet<String>();

        res.add(classReader.getClassName());

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

        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);

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

            if (hasInsnType(method, LineNumberNode.class)) {
                res.add("LineNumberTable");
            }
            if (method.localVariables != null && method.localVariables.size() > 0) {
                res.add("LocalVariableTable");

                String classType = "L" + classNode.name + ';';

                for (LocalVariableNode localVariable : method.localVariables) {
                    if (localVariable.start.getPrevious() == null
                        // && localVariable.end == method.codeLength
                            && localVariable.end.getNext() == null
                            && localVariable.index == 0
                            && localVariable.name.equals("this")
                            && localVariable.desc.equals(classType)) {
                        res.add("this");
                        res.add(classType);
                    }
                }
                for (LocalVariableNode localVariable : method.localVariables) {
                    if (localVariable.signature != null) {
                        res.add("LocalVariableTypeTable");
                    }
                }
            }
            if (hasInsnType(method, FrameNode.class)) {
                res.add("StackMapTable");
            }

            if (method.signature != null) {
                res.add("Signature");
            }

            if (method.exceptions.size() > 0) {
                res.add("Exceptions");
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

            int anonymousClassCount = PackUtils.evaluateAnonymousClassCount(classReader);

            for (int i = 1; i <= anonymousClassCount; i++) {
                res.add(classNode.name + '$' + i);
            }
        }

        if (classNode.signature != null) {
            res.add("Signature");
        }

        if (classNode.outerClass != null) {
            res.add("EnclosingMethod");

            String expectedEnclosingClassName = Utils.generateEnclosingClassName(classNode.name);
            if (classNode.outerClass.equals(expectedEnclosingClassName)) {
                res.add(expectedEnclosingClassName);
            }
        }

        return res;
    }

    private static boolean hasInsnType(MethodNode method, Class<? extends AbstractInsnNode> insnClass) {
        for (Iterator<AbstractInsnNode> itr = method.instructions.iterator(); itr.hasNext(); ) {
            AbstractInsnNode insnNode = itr.next();
            if (insnClass.isAssignableFrom(insnNode.getClass())) {
                return true;
            }
        }

        return false;
    }
}
