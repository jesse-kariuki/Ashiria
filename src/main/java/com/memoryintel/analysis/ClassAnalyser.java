package com.memoryintel.analysis;

import org.objectweb.asm.*;

/**
 * Drives the ASM visitor pipeline for a single class.
 *
 * Pipeline:
 *   raw bytes → ClassReader → InstrumentingClassVisitor → ClassWriter → new bytes
 */


public class ClassAnalyser {

    private ClassAnalyser() {}

    /**
     * Actual instrumentation of a class's bytecode to call AllocationCollector on every NEW.
     *
     * @return instrumented bytes, or null if no NEW instructions were found
     */
    public static byte[] instrument(byte[] classBytes, String internalName){
        ClassReader reader = new ClassReader(classBytes);

        /*
        * COMPUTE_FRAMES: after injecting new bytecode, the stack frame map
          (required by the JVM verifier) must be recalculated. ASM does this
          automatically with this flag. Without it: VerifyError at class load.
        *
        * */

        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        InstrumentingClassVisitor visitor = new InstrumentingClassVisitor(Opcodes.ASM9,writer, internalName);
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        return visitor.didInstrument() ? writer.toByteArray() : null;

    }

    /*
    * Class that acts as a filter, listening for the visitMethod event fired by the class reader
    * and delegating to InstrumentingMethodVisitor to do the actual bytecode injection.
    *
    * */
    static class InstrumentingClassVisitor extends ClassVisitor {
        private final String ownerClass;
        private boolean instrumented = false;

        InstrumentingClassVisitor(int api, ClassVisitor cv, String ownerClass) {
            super(api, cv);
            this.ownerClass = ownerClass;
        }

        void markInstrumented() {
            this.instrumented = true;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

            if ((access & Opcodes.ACC_ABSTRACT) != 0
                    || (access & Opcodes.ACC_NATIVE) != 0) {
                return mv;
            }

            InstrumentingMethodVisitor imv =
                    new InstrumentingMethodVisitor(Opcodes.ASM9, mv, ownerClass, name, this);
            if (imv.isInstrumented()) instrumented = true;
            return imv;
        }

        boolean didInstrument() { return instrumented; }
    }

}


