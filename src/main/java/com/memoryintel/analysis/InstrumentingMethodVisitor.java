package com.memoryintel.analysis;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Visits bytecode instructions one by one. When it sees the pattern:
 *   NEW <type>  ...  INVOKESPECIAL <type>.<init>
 * it injects: DUP + LDC + LDC + INVOKESTATIC AllocationCollector.onAllocation
 */

public class InstrumentingMethodVisitor extends MethodVisitor {

    private static final String COLLECTOR = "com/memoryintel/analysis/AllocationCollector";
    private static final String ON_ALLOC = "onAllocation";
    private static final String ON_ALLOC_DESC = "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V";

    private final String ownerClass;
    private final String ownerMethod;

    private String pendingNewType = null;
    private boolean instrumented  = false;

    public InstrumentingMethodVisitor(int api, MethodVisitor mv, String ownerClass, String ownerMethod) {
        super(api, mv);
        this.ownerClass = ownerClass;
        this.ownerMethod = ownerMethod;
    }

    /**
     * Called for every type instruction (NEW, ANEWARRAY, etc.)
     * We intercept NEW to remember what type is being allocated.
     */
    @Override
    public void visitTypeInsn(int opcode, String type) {
        super.visitTypeInsn(opcode, type);
        if (opcode == Opcodes.NEW) {
            pendingNewType = type;
        }
    }

    /**
     * Called for every method invocation instruction.
     * We look for INVOKESPECIAL <init> that matches the pending NEW.
     */


    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
                                String descriptor, boolean isInterface) {
        // Check if this is a constructor call matching the pending NEW
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

        if(opcode == Opcodes.INVOKESPECIAL
            && "<init>".equals(name)
            && pendingNewType  != null
            && owner.equals(ownerClass)
        ) {

            // Stack : [... objectref, arg1, arg2, ...]. We need to capture the reference before it's consumed by the constructor
            // Inject: DUP + LDC + LDC + INVOKESTATIC AllocationCollector.onAllocation(objectref, allocatingClass, allocatingMethod)
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn(ownerClass);
            mv.visitLdcInsn(ownerMethod);

            /*
            * INVOKESTATIC: calls AllocationCollector.onAllocation(Object, String, String), consuming the top 3 stack items - the duplicated objectref and the two strings.
            * Stack now becomes : [..., objectref] (the original objectref is consumed by the constructor, but the duplicated one remains for the onAllocation call)
            *
            * */
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, COLLECTOR, ON_ALLOC, ON_ALLOC_DESC, false);
            instrumented = true;
            pendingNewType = null;

        }


    }

    public boolean isInstrumented() { return instrumented; }





    }
