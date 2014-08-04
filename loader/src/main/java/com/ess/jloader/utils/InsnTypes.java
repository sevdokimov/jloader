package com.ess.jloader.utils;

/**
 * @author Sergey Evdokimov
 */
public class InsnTypes {

    /**
     * The type of instructions without any argument.
     */
    public static final int NOARG_INSN = 0;

    /**
     * The type of instructions with an signed byte argument.
     */
    public static final int SBYTE_INSN = 1;

    /**
     * The type of instructions with an signed short argument.
     */
    public static final int SHORT_INSN = 2;

    /**
     * The type of instructions with a local variable index argument.
     */
    public static final int VAR_INSN = 3;

    /**
     * The type of instructions with an implicit local variable index argument.
     */
    public static final int IMPLVAR_INSN = 4;

    /**
     * The type of instructions with a type descriptor argument.
     */
    public static final int TYPE_INSN = 5;

    /**
     * The type of field and method invocations instructions.
     */
    public static final int FIELDORMETH_INSN = 6;

    /**
     * The type of the INVOKEINTERFACE/INVOKEDYNAMIC instruction.
     */
    public static final int ITFMETH_INSN = 7;

    /**
     * The type of the INVOKEDYNAMIC instruction.
     */
    public static final int INDYMETH_INSN = 8;

    /**
     * The type of instructions with a 2 bytes bytecode offset label.
     */
    public static final int LABEL_INSN = 9;

    /**
     * The type of instructions with a 4 bytes bytecode offset label.
     */
    public static final int LABELW_INSN = 10;

    /**
     * The type of the LDC instruction.
     */
    public static final int LDC_INSN = 11;

    /**
     * The type of the LDC_W and LDC2_W instructions.
     */
    public static final int LDCW_INSN = 12;

    /**
     * The type of the IINC instruction.
     */
    public static final int IINC_INSN = 13;

    /**
     * The type of the TABLESWITCH instruction.
     */
    public static final int TABL_INSN = 14;

    /**
     * The type of the LOOKUPSWITCH instruction.
     */
    public static final int LOOK_INSN = 15;

    /**
     * The type of the MULTIANEWARRAY instruction.
     */
    public static final int MANA_INSN = 16;

    /**
     * The type of the WIDE instruction.
     */
    public static final int WIDE_INSN = 17;


    public static final byte[] TYPE;

    /**
     * Computes the instruction types of JVM opcodes.
     */
    static {
        int i;
        byte[] b = new byte[220];
        String s = "AAAAAAAAAAAAAAAABCLMMDDDDDEEEEEEEEEEEEEEEEEEEEAAAAAAAADD"
                + "DDDEEEEEEEEEEEEEEEEEEEEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                + "AAAAAAAAAAAAAAAAANAAAAAAAAAAAAAAAAAAAAJJJJJJJJJJJJJJJJDOPAA"
                + "AAAAGGGGGGGHIFBFAAFFAARQJJKKJJJJJJJJJJJJJJJJJJ";
        for (i = 0; i < b.length; ++i) {
            b[i] = (byte) (s.charAt(i) - 'A');
        }
        TYPE = b;
    }

}
