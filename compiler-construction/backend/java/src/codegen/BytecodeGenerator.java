
package codegen;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.objectweb.asm.Opcodes.*;

public class BytecodeGenerator {

    public static class Result {
        public final String bytecode;
        public final String filePath;

        public Result(String bytecode, String filePath) {
            this.bytecode = bytecode;
            this.filePath = filePath;
        }
    }

    private final Map<String, Integer> variables = new HashMap<>();
    private int nextSlot = 1;
    private MethodVisitor method;

    public Result generate(List<String> instructions) throws Exception {

        variables.clear();
        nextSlot = 1;

        ClassWriter writer = new ClassWriter(
            ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS
        );

        writer.visit(
            V17,
            ACC_PUBLIC | ACC_SUPER,
            "GeneratedProgram",
            null,
            "java/lang/Object",
            null
        );

        MethodVisitor constructor = writer.visitMethod(
            ACC_PUBLIC, "<init>", "()V", null, null
        );

        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(
            INVOKESPECIAL,
            "java/lang/Object",
            "<init>",
            "()V",
            false
        );
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();

        method = writer.visitMethod(
            ACC_PUBLIC | ACC_STATIC,
            "main",
            "([Ljava/lang/String;)V",
            null,
            null
        );

        method.visitCode();

        for (String instruction : instructions) {
            compileInstruction(instruction.trim());
        }

        method.visitInsn(RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();

        writer.visitEnd();

        byte[] classBytes = writer.toByteArray();

        File directory = new File("target/generated-bytecode");
        directory.mkdirs();

        File classFile = new File(
            directory,
            "GeneratedProgram.class"
        );

        try (FileOutputStream output =
                 new FileOutputStream(classFile)) {
            output.write(classBytes);
        }

        StringWriter text = new StringWriter();

        TraceClassVisitor tracer = new TraceClassVisitor(
            new PrintWriter(text)
        );

        ClassWriter displayWriter = new ClassWriter(0);
        displayWriter.visit(
            V17,
            ACC_PUBLIC | ACC_SUPER,
            "GeneratedProgram",
            null,
            "java/lang/Object",
            null
        );

        MethodVisitor displayMethod = displayWriter.visitMethod(
            ACC_PUBLIC | ACC_STATIC,
            "main",
            "([Ljava/lang/String;)V",
            null,
            null
        );

        displayMethod.visitCode();
        displayMethod.visitInsn(RETURN);
        displayMethod.visitMaxs(0, 0);
        displayMethod.visitEnd();
        displayWriter.visitEnd();

        new ClassReaderHelper().accept(classBytes, tracer);

        return new Result(
            text.toString(),
            classFile.getAbsolutePath()
        );
    }

    private void compileInstruction(String line) {

        if (line.isEmpty()) return;

        if (line.startsWith("print ")) {
            String expression = line.substring(6).trim();

            method.visitFieldInsn(
                GETSTATIC,
                "java/lang/System",
                "out",
                "Ljava/io/PrintStream;"
            );

            boolean integer = emitValue(expression);

            method.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/io/PrintStream",
                "println",
                integer ? "(I)V" : "(Ljava/lang/String;)V",
                false
            );

            return;
        }

        Matcher assignment = Pattern.compile(
            "^([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*=\\s*(.+)$"
        ).matcher(line);

        if (!assignment.matches()) {
            throw new IllegalArgumentException(
                "Unsupported TAC instruction: " + line
            );
        }

        String name = assignment.group(1);
        String expression = assignment.group(2).trim();

        Matcher arithmetic = Pattern.compile(
            "^(.+?)\\s*([+\\-*/])\\s*(.+)$"
        ).matcher(expression);

        if (arithmetic.matches()) {

            boolean leftInteger = emitValue(
                arithmetic.group(1).trim()
            );

            boolean rightInteger = emitValue(
                arithmetic.group(3).trim()
            );

            if (!leftInteger || !rightInteger) {
                throw new IllegalArgumentException(
                    "Only integer arithmetic is currently supported."
                );
            }

            switch (arithmetic.group(2)) {
                case "+":
                    method.visitInsn(IADD);
                    break;
                case "-":
                    method.visitInsn(ISUB);
                    break;
                case "*":
                    method.visitInsn(IMUL);
                    break;
                case "/":
                    method.visitInsn(IDIV);
                    break;
            }

        } else {
            emitValue(expression);
        }

        int slot = variables.computeIfAbsent(
            name,
            key -> nextSlot++
        );

        method.visitVarInsn(ISTORE, slot);
    }

    private boolean emitValue(String expression) {

        expression = expression.trim();

        if (expression.matches("-?\\d+")) {
            int value = Integer.parseInt(expression);
            method.visitLdcInsn(value);
            return true;
        }

        if (expression.equals("true")) {
            method.visitInsn(ICONST_1);
            return true;
        }

        if (expression.equals("false")) {
            method.visitInsn(ICONST_0);
            return true;
        }

        if (expression.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) {

            Integer slot = variables.get(expression);

            if (slot == null) {
                throw new IllegalArgumentException(
                    "Unknown TAC variable: " + expression
                );
            }

            method.visitVarInsn(ILOAD, slot);
            return true;
        }

        if (expression.startsWith("\"")
                && expression.endsWith("\"")) {
            method.visitLdcInsn(
                expression.substring(1, expression.length() - 1)
            );
            return false;
        }

        throw new IllegalArgumentException(
            "Unsupported TAC expression: " + expression
        );
    }

    private static class ClassReaderHelper {
        void accept(byte[] bytes, TraceClassVisitor visitor) {
            new org.objectweb.asm.ClassReader(bytes).accept(
                visitor, 0
            );
        }
    }
}
