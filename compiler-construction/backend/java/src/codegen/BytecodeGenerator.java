
package codegen;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
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
import util.JavaIdentifiers;

import static org.objectweb.asm.Opcodes.*;

public class BytecodeGenerator {

    private enum ValueType {
        INT, DOUBLE, BOOLEAN, STRING, INT_ARRAY
    }

    public static class Result {
        public final String bytecode;
        public final String filePath;

        public Result(String bytecode, String filePath) {
            this.bytecode = bytecode;
            this.filePath = filePath;
        }
    }

    private final Map<String, Integer> variables = new HashMap<>();
    private final Map<String, ValueType> variableTypes = new HashMap<>();
    private final Map<String, Label> labels = new HashMap<>();
    private int nextSlot = 2;
    private MethodVisitor method;

    public Result generate(String className, List<String> instructions)
            throws Exception {

        if (!JavaIdentifiers.isValidClassIdentifier(className)) {
            throw new IllegalArgumentException(
                "Code Generation Error: Invalid Java class identifier: "
                + className
            );
        }

        variables.clear();
        variableTypes.clear();
        labels.clear();
        nextSlot = 2;

        ClassWriter writer = new ClassWriter(
            ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS
        );

        writer.visit(
            V17,
            ACC_PUBLIC | ACC_SUPER,
            className,
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
        method.visitTypeInsn(NEW, "java/util/Scanner");
        method.visitInsn(DUP);
        method.visitFieldInsn(
            GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;"
        );
        method.visitMethodInsn(
            INVOKESPECIAL, "java/util/Scanner", "<init>",
            "(Ljava/io/InputStream;)V", false
        );
        method.visitVarInsn(ASTORE, 1);

        for (String instruction : instructions) {
            String line = instruction.trim();
            if (line.endsWith(":")) {
                labels.put(
                    line.substring(0, line.length() - 1),
                    new Label()
                );
            }
        }

        for (String instruction : instructions) {
            compileInstruction(instruction.trim());
        }

        method.visitVarInsn(ALOAD, 1);
        method.visitMethodInsn(
            INVOKEVIRTUAL, "java/util/Scanner", "close", "()V", false
        );
        method.visitInsn(RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();

        writer.visitEnd();

        byte[] classBytes = writer.toByteArray();

        File directory = new File("target/generated-bytecode");
        directory.mkdirs();

        File classFile = new File(
            directory,
            className + ".class"
        );

        try (FileOutputStream output =
                 new FileOutputStream(classFile)) {
            output.write(classBytes);
        }

        StringWriter text = new StringWriter();

        TraceClassVisitor tracer = new TraceClassVisitor(
            new PrintWriter(text)
        );

        new ClassReaderHelper().accept(classBytes, tracer);

        return new Result(
            text.toString(),
            classFile.getAbsolutePath()
        );
    }

    private void compileInstruction(String line) {

        if (line.isEmpty()) return;

        if (line.endsWith(":")) {
            method.visitLabel(labels.get(
                line.substring(0, line.length() - 1)
            ));
            return;
        }

        Matcher conditional = Pattern.compile(
            "^ifFalse\\s+(.+)\\s+goto\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)$"
        ).matcher(line);

        if (conditional.matches()) {
            ValueType conditionType = emitExpression(
                conditional.group(1).trim()
            );
            if (conditionType == ValueType.DOUBLE) {
                method.visitInsn(DCONST_0);
                method.visitInsn(DCMPL);
            }
            method.visitJumpInsn(
                IFEQ,
                labels.get(conditional.group(2))
            );
            return;
        }

        Matcher jump = Pattern.compile(
            "^goto\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)$"
        ).matcher(line);

        if (jump.matches()) {
            method.visitJumpInsn(GOTO, labels.get(jump.group(1)));
            return;
        }

        if (line.startsWith("print ") || line.startsWith("write ")) {
            boolean newline = line.startsWith("print ");
            String expression = line.substring(6).trim();

            method.visitFieldInsn(
                GETSTATIC,
                "java/lang/System",
                "out",
                "Ljava/io/PrintStream;"
            );

            ValueType valueType = emitExpression(expression);
            String descriptor;
            switch (valueType) {
                case INT:
                    descriptor = "(I)V";
                    break;
                case DOUBLE:
                    descriptor = "(D)V";
                    break;
                case BOOLEAN:
                    descriptor = "(Z)V";
                    break;
                case STRING:
                    descriptor = "(Ljava/lang/String;)V";
                    break;
                default:
                    throw new IllegalStateException("Unsupported print type.");
            }

            method.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/io/PrintStream",
                newline ? "println" : "print",
                descriptor,
                false
            );

            return;
        }

        Matcher arrayAssignment = Pattern.compile(
            "^([a-zA-Z_$][a-zA-Z0-9_$]*)\\[(.*)]\\s*=\\s*(.*)$"
        ).matcher(line);

        if (arrayAssignment.matches()) {
            String name = arrayAssignment.group(1);
            Integer slot = variables.get(name);
            if (slot == null || variableTypes.get(name) != ValueType.INT_ARRAY) {
                throw new IllegalArgumentException(
                    "Unknown int array in TAC assignment: " + name
                );
            }
            method.visitVarInsn(ALOAD, slot);
            ValueType indexType = emitExpression(arrayAssignment.group(2));
            convert(indexType, ValueType.INT);
            ValueType valueType = emitExpression(arrayAssignment.group(3));
            convert(valueType, ValueType.INT);
            method.visitInsn(IASTORE);
            return;
        }

        Matcher assignment = Pattern.compile(
            "^([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*=\\s*(.*)$"
        ).matcher(line);

        if (!assignment.matches()) {
            throw new IllegalArgumentException(
                "Unsupported TAC instruction: " + line
            );
        }

        String name = assignment.group(1);
        String expression = assignment.group(2).trim();
        ValueType valueType = emitExpression(expression);
        ValueType declaredType = variableTypes.get(name);
        if (declaredType == null) {
            declaredType = valueType;
            variableTypes.put(name, declaredType);
            variables.put(name, nextSlot);
            nextSlot += declaredType == ValueType.DOUBLE ? 2 : 1;
        } else {
            convert(valueType, declaredType);
        }
        method.visitVarInsn(storeOpcode(declaredType), variables.get(name));
    }

    private ValueType emitExpression(String expression) {
        expression = expression.trim();
        ValueType type = inferType(expression);

        Matcher cast = Pattern.compile("^(int|float|double)\\((.*)\\)$")
            .matcher(expression);
        if (cast.matches()) {
            ValueType sourceType = emitExpression(cast.group(2));
            ValueType targetType = cast.group(1).equals("int")
                ? ValueType.INT : ValueType.DOUBLE;
            convert(sourceType, targetType);
            return targetType;
        }

        Matcher arrayAllocation = Pattern.compile("^newarray\\s+int\\s+(.+)$")
            .matcher(expression);
        if (arrayAllocation.matches()) {
            ValueType sizeType = emitExpression(arrayAllocation.group(1));
            convert(sizeType, ValueType.INT);
            method.visitIntInsn(NEWARRAY, T_INT);
            return ValueType.INT_ARRAY;
        }

        Matcher arrayAccess = Pattern.compile(
            "^([a-zA-Z_$][a-zA-Z0-9_$]*)\\[(.*)]$"
        ).matcher(expression);
        if (arrayAccess.matches()) {
            Integer slot = variables.get(arrayAccess.group(1));
            if (slot == null
                    || variableTypes.get(arrayAccess.group(1)) != ValueType.INT_ARRAY) {
                throw new IllegalArgumentException(
                    "Unknown int array in TAC expression: " + arrayAccess.group(1)
                );
            }
            method.visitVarInsn(ALOAD, slot);
            ValueType indexType = emitExpression(arrayAccess.group(2));
            convert(indexType, ValueType.INT);
            method.visitInsn(IALOAD);
            return ValueType.INT;
        }

        if (expression.matches("-?\\d+(\\.\\d+)?")
                || expression.equals("true")
                || expression.equals("false")
                || (expression.startsWith("\"") && expression.endsWith("\""))
                || expression.matches("read(Int|Float|Double|Line)")
                || expression.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) {
            return emitValue(expression);
        }

        Matcher comparison = Pattern.compile(
            "^(.+?)\\s*(==|!=|<=|>=|<|>)\\s*(.+)$"
        ).matcher(expression);
        if (comparison.matches()) {
            emitComparison(
                comparison.group(1).trim(),
                comparison.group(2),
                comparison.group(3).trim()
            );
            return ValueType.BOOLEAN;
        }

        Matcher logical = Pattern.compile("^(.+?)\\s*(&&|\\|\\|)\\s*(.+)$")
            .matcher(expression);
        if (logical.matches()) {
            emitExpression(logical.group(1));
            emitExpression(logical.group(3));
            method.visitInsn(logical.group(2).equals("&&") ? IAND : IOR);
            return ValueType.BOOLEAN;
        }

        Matcher arithmetic = Pattern.compile(
            "^(.+?)\\s*([+\\-*/%])\\s*(.+)$"
        ).matcher(expression);
        if (arithmetic.matches()) {
            String left = arithmetic.group(1).trim();
            String operator = arithmetic.group(2);
            String right = arithmetic.group(3).trim();
            ValueType leftType = inferType(left);
            ValueType rightType = inferType(right);
            if (operator.equals("+")
                    && (leftType == ValueType.STRING
                        || rightType == ValueType.STRING)) {
                method.visitTypeInsn(NEW, "java/lang/StringBuilder");
                method.visitInsn(DUP);
                method.visitMethodInsn(
                    INVOKESPECIAL,
                    "java/lang/StringBuilder",
                    "<init>",
                    "()V",
                    false
                );
                appendStringValue(left, leftType);
                appendStringValue(right, rightType);
                method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "java/lang/StringBuilder",
                    "toString",
                    "()Ljava/lang/String;",
                    false
                );
                return ValueType.STRING;
            }
            ValueType resultType = inferType(expression);
            leftType = emitExpression(left);
            convert(leftType, resultType);
            rightType = emitExpression(right);
            convert(rightType, resultType);

            if (resultType == ValueType.DOUBLE) {
                method.visitInsn(doubleArithmeticOpcode(operator));
            } else {
                method.visitInsn(intArithmeticOpcode(operator));
            }
            return resultType;
        }

        Matcher unary = Pattern.compile("^([!+\\-])\\s*(.+)$")
            .matcher(expression);
        if (unary.matches()) {
            String operator = unary.group(1);
            ValueType operandType = emitExpression(unary.group(2));
            if (operator.equals("!")) {
                method.visitInsn(ICONST_1);
                method.visitInsn(IXOR);
                return ValueType.BOOLEAN;
            }
            if (operator.equals("-")) {
                method.visitInsn(operandType == ValueType.DOUBLE ? DNEG : INEG);
            }
            return operandType;
        }

        return emitValue(expression);
    }

    private ValueType inferType(String expression) {
        expression = expression.trim();
        if (expression.matches("^(int|float|double)\\(.*\\)$")) {
            return expression.startsWith("int(")
                ? ValueType.INT : ValueType.DOUBLE;
        }
        if (expression.matches("^newarray\\s+int\\s+.+$")) {
            return ValueType.INT_ARRAY;
        }
        if (expression.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\[.*]$")) {
            return ValueType.INT;
        }
        if (expression.equals("true") || expression.equals("false")) {
            return ValueType.BOOLEAN;
        }
        if (expression.startsWith("\"") && expression.endsWith("\"")) {
            return ValueType.STRING;
        }
        if (expression.matches("-?\\d+\\.\\d+")) return ValueType.DOUBLE;
        if (expression.matches("-?\\d+")) return ValueType.INT;
        if (expression.equals("readInt")) return ValueType.INT;
        if (expression.equals("readFloat")
                || expression.equals("readDouble")) return ValueType.DOUBLE;
        if (expression.equals("readLine")) return ValueType.STRING;

        Matcher comparison = Pattern.compile(
            "^(.+?)\\s*(==|!=|<=|>=|<|>)\\s*(.+)$"
        ).matcher(expression);
        if (comparison.matches()) return ValueType.BOOLEAN;
        Matcher logical = Pattern.compile("^(.+?)\\s*(&&|\\|\\|)\\s*(.+)$")
            .matcher(expression);
        if (logical.matches()) return ValueType.BOOLEAN;

        Matcher arithmetic = Pattern.compile(
            "^(.+?)\\s*([+\\-*/%])\\s*(.+)$"
        ).matcher(expression);
        if (arithmetic.matches()) {
            if (arithmetic.group(2).equals("+")
                    && (inferType(arithmetic.group(1)) == ValueType.STRING
                        || inferType(arithmetic.group(3)) == ValueType.STRING)) {
                return ValueType.STRING;
            }
            return inferType(arithmetic.group(1)) == ValueType.DOUBLE
                    || inferType(arithmetic.group(3)) == ValueType.DOUBLE
                ? ValueType.DOUBLE : ValueType.INT;
        }
        Matcher unary = Pattern.compile("^([!+\\-])\\s*(.+)$")
            .matcher(expression);
        if (unary.matches()) {
            return unary.group(1).equals("!")
                ? ValueType.BOOLEAN : inferType(unary.group(2));
        }

        ValueType variableType = variableTypes.get(expression);
        if (variableType == null) {
            throw new IllegalArgumentException(
                "Unknown TAC variable or expression: " + expression
            );
        }
        return variableType;
    }

    private void appendStringValue(String expression, ValueType valueType) {
        emitExpression(expression);
        String descriptor;
        switch (valueType) {
            case INT:
                descriptor = "(I)Ljava/lang/StringBuilder;";
                break;
            case DOUBLE:
                descriptor = "(D)Ljava/lang/StringBuilder;";
                break;
            case BOOLEAN:
                descriptor = "(Z)Ljava/lang/StringBuilder;";
                break;
            case STRING:
                descriptor = "(Ljava/lang/String;)Ljava/lang/StringBuilder;";
                break;
            default:
                throw new IllegalArgumentException(
                    "Unsupported string concatenation type: " + valueType
                );
        }
        method.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "append",
            descriptor,
            false
        );
    }

    private void emitComparison(String left, String operator, String right) {
        ValueType leftType = inferType(left);
        ValueType rightType = inferType(right);
        boolean useDouble = leftType == ValueType.DOUBLE
            || rightType == ValueType.DOUBLE;

        leftType = emitExpression(left);
        if (useDouble) convert(leftType, ValueType.DOUBLE);
        rightType = emitExpression(right);
        if (useDouble) convert(rightType, ValueType.DOUBLE);

        int opcode;
        if (useDouble) {
            method.visitInsn(DCMPL);
            switch (operator) {
                case "==": opcode = IFEQ; break;
                case "!=": opcode = IFNE; break;
                case "<": opcode = IFLT; break;
                case "<=": opcode = IFLE; break;
                case ">": opcode = IFGT; break;
                case ">=": opcode = IFGE; break;
                default:
                    throw new IllegalArgumentException(
                        "Unsupported comparison operator: " + operator
                    );
            }
        } else {
            switch (operator) {
                case "==": opcode = IF_ICMPEQ; break;
                case "!=": opcode = IF_ICMPNE; break;
                case "<": opcode = IF_ICMPLT; break;
                case "<=": opcode = IF_ICMPLE; break;
                case ">": opcode = IF_ICMPGT; break;
                case ">=": opcode = IF_ICMPGE; break;
                default:
                    throw new IllegalArgumentException(
                        "Unsupported comparison operator: " + operator
                    );
            }
        }

        Label trueLabel = new Label();
        Label endLabel = new Label();
        method.visitJumpInsn(opcode, trueLabel);
        method.visitInsn(ICONST_0);
        method.visitJumpInsn(GOTO, endLabel);
        method.visitLabel(trueLabel);
        method.visitInsn(ICONST_1);
        method.visitLabel(endLabel);
    }

    private ValueType emitValue(String expression) {
        expression = expression.trim();

        if (expression.matches("-?\\d+")) {
            method.visitLdcInsn(Integer.parseInt(expression));
            return ValueType.INT;
        }
        if (expression.matches("-?\\d+\\.\\d+")) {
            method.visitLdcInsn(Double.parseDouble(expression));
            return ValueType.DOUBLE;
        }
        if (expression.equals("true") || expression.equals("false")) {
            method.visitInsn(expression.equals("true") ? ICONST_1 : ICONST_0);
            return ValueType.BOOLEAN;
        }
        if (expression.startsWith("\"") && expression.endsWith("\"")) {
            method.visitLdcInsn(
                expression.substring(1, expression.length() - 1)
            );
            return ValueType.STRING;
        }
        if (expression.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\[.*]$")) {
            return emitExpression(expression);
        }
        if (expression.startsWith("read")) {
            String methodName;
            String descriptor;
            switch (expression) {
                case "readInt":
                    methodName = "nextInt";
                    descriptor = "()I";
                    break;
                case "readFloat":
                    methodName = "nextFloat";
                    descriptor = "()F";
                    break;
                case "readDouble":
                    methodName = "nextDouble";
                    descriptor = "()D";
                    break;
                case "readLine":
                    methodName = "nextLine";
                    descriptor = "()Ljava/lang/String;";
                    break;
                default:
                    throw new IllegalArgumentException(
                        "Unsupported input operation: " + expression
                    );
            }
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(
                INVOKEVIRTUAL, "java/util/Scanner", methodName,
                descriptor, false
            );
            if (expression.equals("readFloat")) method.visitInsn(F2D);
            return inferType(expression);
        }

        Integer slot = variables.get(expression);
        ValueType type = variableTypes.get(expression);
        if (slot != null && type != null) {
            method.visitVarInsn(loadOpcode(type), slot);
            return type;
        }

        throw new IllegalArgumentException(
            "Unknown TAC variable or expression: " + expression
        );
    }

    private void convert(ValueType from, ValueType to) {
        if (from == to) return;
        if (from == ValueType.INT && to == ValueType.DOUBLE) {
            method.visitInsn(I2D);
            return;
        }
        if (from == ValueType.DOUBLE && to == ValueType.INT) {
            method.visitInsn(D2I);
            return;
        }
        if (from == ValueType.BOOLEAN && to == ValueType.INT) return;
        throw new IllegalArgumentException(
            "Cannot convert " + from + " to " + to + "."
        );
    }

    private int intArithmeticOpcode(String operator) {
        switch (operator) {
            case "+": return IADD;
            case "-": return ISUB;
            case "*": return IMUL;
            case "/": return IDIV;
            case "%": return IREM;
            default: throw new IllegalArgumentException(
                "Unsupported arithmetic operator: " + operator
            );
        }
    }

    private int doubleArithmeticOpcode(String operator) {
        switch (operator) {
            case "+": return DADD;
            case "-": return DSUB;
            case "*": return DMUL;
            case "/": return DDIV;
            case "%": return DREM;
            default: throw new IllegalArgumentException(
                "Unsupported arithmetic operator: " + operator
            );
        }
    }

    private int loadOpcode(ValueType type) {
        switch (type) {
            case DOUBLE: return DLOAD;
            case STRING:
            case INT_ARRAY: return ALOAD;
            default: return ILOAD;
        }
    }

    private int storeOpcode(ValueType type) {
        switch (type) {
            case DOUBLE: return DSTORE;
            case STRING:
            case INT_ARRAY: return ASTORE;
            default: return ISTORE;
        }
    }

    private static class ClassReaderHelper {
        void accept(byte[] bytes, TraceClassVisitor visitor) {
            new org.objectweb.asm.ClassReader(bytes).accept(
                visitor, 0
            );
        }
    }
}
