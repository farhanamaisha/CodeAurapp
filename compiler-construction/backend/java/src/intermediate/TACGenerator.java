
package intermediate;

import ast.ASTNode;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TACGenerator {

    private final List<String> code = new ArrayList<>();
    private final Map<String, String> constants = new HashMap<>();
    private final Deque<String> breakTargets = new ArrayDeque<>();

    private int tempCount;
    private int labelCount;
    private boolean optimizationEnabled;

    // Phase 4: Generate original, unoptimized three-address code.
    public List<String> generate(ASTNode root) {
        return generateInternal(root, false);
    }

    // Phase 5: Generate optimized three-address code.
    public List<String> optimize(ASTNode root) {
        return generateInternal(root, true);
    }

    private List<String> generateInternal(
            ASTNode root, boolean enableOptimization) {

        code.clear();
        constants.clear();
        breakTargets.clear();

        tempCount = 0;
        labelCount = 0;
        optimizationEnabled = enableOptimization;

        generateNode(root);

        return new ArrayList<>(code);
    }

    private void generateNode(ASTNode node) {
        if (node == null) return;

        switch (node.getType()) {

            case "PROGRAM":
            case "BLOCK":
                for (ASTNode child : node.getChildren()) {
                    generateNode(child);
                }
                break;

            case "DECLARATION":
                generateDeclaration(node);
                break;

            case "ARRAY_DECL":
                generateArrayDeclaration(node);
                break;

            case "ASSIGNMENT":
                generateAssignment(node);
                break;

            case "ARRAY_ASSIGN":
                generateArrayAssignment(node);
                break;

            case "PRINT":
                if (!node.getChildren().isEmpty()) {
                    String instruction = "print".equals(node.getValue())
                        ? "write " : "print ";
                    code.add(instruction +
                        generateExpression(node.getChildren().get(0)));
                }
                break;

            case "IF":
                generateIf(node);
                break;

            case "WHILE":
                generateWhile(node);
                break;

            case "FOR":
                generateFor(node);
                break;

            case "SWITCH":
                generateSwitch(node);
                break;

            case "BREAK":
                if (!breakTargets.isEmpty()) {
                    code.add("goto " + breakTargets.peek());
                }
                break;

            default:
                break;
        }
    }

    private void generateDeclaration(ASTNode node) {

        List<ASTNode> children = node.getChildren();

        if (children.isEmpty()) return;

        String variable = children.get(0).getValue();

        if (children.size() > 1) {

            String value = generateExpression(children.get(1));

            code.add(variable + " = " + value);

            if (optimizationEnabled && isLiteral(value)) {
                constants.put(variable, value);
            } else {
                constants.remove(variable);
            }

        } else {
            constants.remove(variable);
        }
    }

    private void generateAssignment(ASTNode node) {

        if (node.getChildren().isEmpty()) return;

        String variable = node.getValue();
        String value = generateExpression(node.getChildren().get(0));

        code.add(variable + " = " + value);

        if (optimizationEnabled && isLiteral(value)) {
            constants.put(variable, value);
        } else {
            constants.remove(variable);
        }
    }

    private void generateArrayDeclaration(ASTNode node) {
        String name = node.getChildren().get(0).getValue();
        String size = generateExpression(node.getChildren().get(1));
        code.add(name + " = newarray int " + size);
        constants.remove(name);
    }

    private void generateArrayAssignment(ASTNode node) {
        String index = generateExpression(node.getChildren().get(0));
        String value = generateExpression(node.getChildren().get(1));
        code.add(node.getValue() + "[" + index + "] = " + value);
    }

    private void generateIf(ASTNode node) {

        List<ASTNode> children = node.getChildren();

        if (children.size() < 2) return;

        String condition = generateExpression(children.get(0));

        String elseLabel = newLabel();
        String endLabel = newLabel();

        code.add("ifFalse " + condition + " goto " + elseLabel);

        constants.clear();
        generateNode(children.get(1));

        if (children.size() > 2) {

            code.add("goto " + endLabel);
            code.add(elseLabel + ":");

            constants.clear();
            generateNode(children.get(2));

            code.add(endLabel + ":");

        } else {
            code.add(elseLabel + ":");
        }

        constants.clear();
    }

    private void generateWhile(ASTNode node) {

        List<ASTNode> children = node.getChildren();

        if (children.size() < 2) return;

        constants.clear();

        String start = newLabel();
        String end = newLabel();

        code.add(start + ":");

        String condition = generateExpression(children.get(0));

        code.add("ifFalse " + condition + " goto " + end);

        constants.clear();
        breakTargets.push(end);
        generateNode(children.get(1));
        breakTargets.pop();

        code.add("goto " + start);
        code.add(end + ":");

        constants.clear();
    }

    private void generateFor(ASTNode node) {

        List<ASTNode> children = node.getChildren();

        if (children.size() < 4) return;

        generateNode(children.get(0));

        constants.clear();

        String start = newLabel();
        String end = newLabel();

        code.add(start + ":");

        if (!children.get(1).getType().equals("EMPTY")) {
            String condition = generateExpression(children.get(1));
            code.add("ifFalse " + condition + " goto " + end);
        }

        constants.clear();
        breakTargets.push(end);
        generateNode(children.get(3));
        breakTargets.pop();

        constants.clear();

        if (!children.get(2).getType().equals("EMPTY")) {
            generateNode(children.get(2));
        }

        code.add("goto " + start);
        code.add(end + ":");

        constants.clear();
    }

    private void generateSwitch(ASTNode node) {
        List<ASTNode> children = node.getChildren();
        if (children.isEmpty()) return;

        String selector = generateExpression(children.get(0));
        String end = newLabel();
        String defaultLabel = end;
        List<String> branchLabels = new ArrayList<>();

        for (int i = 1; i < children.size(); i++) {
            ASTNode branch = children.get(i);
            String branchLabel = newLabel();
            branchLabels.add(branchLabel);

            if (branch.getType().equals("DEFAULT")) {
                defaultLabel = branchLabel;
                continue;
            }

            String nextCheck = newLabel();
            ASTNode comparison = new ASTNode("BINARY", "==", node.getLine());
            comparison.addChild(new ASTNode("LITERAL", selector, node.getLine()));
            comparison.addChild(branch.getChildren().get(0));
            String condition = generateExpression(comparison);
            code.add("ifFalse " + condition + " goto " + nextCheck);
            code.add("goto " + branchLabel);
            code.add(nextCheck + ":");
        }

        code.add("goto " + defaultLabel);
        breakTargets.push(end);

        for (int i = 1; i < children.size(); i++) {
            ASTNode branch = children.get(i);
            code.add(branchLabels.get(i - 1) + ":");
            ASTNode body = branch.getChildren().get(branch.getChildren().size() - 1);
            generateNode(body);
        }

        breakTargets.pop();
        code.add(end + ":");
        constants.clear();
    }

    private String generateExpression(ASTNode node) {

        if (node == null) return "";

        switch (node.getType()) {

            case "LITERAL":
                return node.getValue();

            case "IDENTIFIER": {
                String variable = node.getValue();

                if (optimizationEnabled) {
                    return constants.getOrDefault(variable, variable);
                }

                return variable;
            }

            case "ARRAY_ACCESS":
                return node.getValue() + "["
                    + generateExpression(node.getChildren().get(0)) + "]";

            case "BINARY": {

                String left =
                    generateExpression(node.getChildren().get(0));

                String right =
                    generateExpression(node.getChildren().get(1));

                String operator = node.getValue();

                if (optimizationEnabled) {
                    String folded = foldBinary(operator, left, right);

                    if (folded != null) {
                        return folded;
                    }
                }

                String temp = newTemp();

                code.add(temp + " = " + left + " " +
                    operator + " " + right);

                return temp;
            }

            case "UNARY": {

                String operand =
                    generateExpression(node.getChildren().get(0));

                String operator = node.getValue();

                if (optimizationEnabled) {
                    String folded = foldUnary(operator, operand);

                    if (folded != null) {
                        return folded;
                    }
                }

                String temp = newTemp();

                code.add(temp + " = " + operator + operand);

                return temp;
            }

            case "INPUT":
                switch (node.getValue()) {
                    case "int": return "readInt";
                    case "float": return "readFloat";
                    case "double": return "readDouble";
                    case "String": return "readLine";
                    default:
                        throw new IllegalArgumentException(
                            "Unsupported Scanner input type: " + node.getValue()
                        );
                }

            case "CAST":
                return node.getValue() + "("
                    + generateExpression(node.getChildren().get(0)) + ")";

            default:
                return "";
        }
    }

    private String foldBinary(
            String operator, String left, String right) {

        try {

            if (operator.equals("&&") || operator.equals("||")) {

                if (!isBoolean(left) || !isBoolean(right)) {
                    return null;
                }

                boolean a = Boolean.parseBoolean(left);
                boolean b = Boolean.parseBoolean(right);

                return String.valueOf(
                    operator.equals("&&") ? a && b : a || b
                );
            }

            if (operator.equals("==") || operator.equals("!=")) {

                if (isNumeric(left) && isNumeric(right)) {

                    int comparison =
                        new BigDecimal(left).compareTo(new BigDecimal(right));

                    return String.valueOf(
                        operator.equals("==")
                            ? comparison == 0
                            : comparison != 0
                    );
                }

                if (isBoolean(left) && isBoolean(right)) {

                    boolean equal = left.equals(right);

                    return String.valueOf(
                        operator.equals("==") ? equal : !equal
                    );
                }

                return null;
            }

            if (!isNumeric(left) || !isNumeric(right)) {
                return null;
            }

            BigDecimal a = new BigDecimal(left);
            BigDecimal b = new BigDecimal(right);

            switch (operator) {

                case "+":
                    return format(a.add(b));

                case "-":
                    return format(a.subtract(b));

                case "*":
                    return format(a.multiply(b));

                case "/":
                    if (b.compareTo(BigDecimal.ZERO) == 0) {
                        return null;
                    }
                    return format(a.divide(b, MathContext.DECIMAL128));

                case "%":
                    if (b.compareTo(BigDecimal.ZERO) == 0) {
                        return null;
                    }
                    return format(a.remainder(b));

                case "<":
                    return String.valueOf(a.compareTo(b) < 0);

                case ">":
                    return String.valueOf(a.compareTo(b) > 0);

                case "<=":
                    return String.valueOf(a.compareTo(b) <= 0);

                case ">=":
                    return String.valueOf(a.compareTo(b) >= 0);

                default:
                    return null;
            }

        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }

    private String foldUnary(String operator, String operand) {

        try {

            if (operator.equals("-") && isNumeric(operand)) {
                return format(new BigDecimal(operand).negate());
            }

            if (operator.equals("!") && isBoolean(operand)) {
                return String.valueOf(!Boolean.parseBoolean(operand));
            }

        } catch (NumberFormatException exception) {
            return null;
        }

        return null;
    }

    private boolean isLiteral(String value) {
        return isNumeric(value)
            || isBoolean(value)
            || (value != null
                && value.startsWith("\"")
                && value.endsWith("\""));
    }

    private boolean isNumeric(String value) {

        if (value == null) return false;

        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean isBoolean(String value) {
        return "true".equals(value) || "false".equals(value);
    }

    private String format(BigDecimal number) {
        return number.stripTrailingZeros().toPlainString();
    }

    private String newTemp() {
        return "t" + (++tempCount);
    }

    private String newLabel() {
        return "L" + (++labelCount);
    }
}
