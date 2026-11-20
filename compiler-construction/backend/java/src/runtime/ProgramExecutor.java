package runtime;

import ast.ASTNode;
import java.util.*;

public class ProgramExecutor {
    private final Map<String, Object> variables = new LinkedHashMap<>();
    private final Queue<String> inputs = new LinkedList<>();
    private final List<String> output = new ArrayList<>();
    private static final int MAX_STEPS = 100000;
    private int steps = 0;

    public List<String> execute(ASTNode root, String input) {
        variables.clear();
        output.clear();
        inputs.clear();
        steps = 0;

        if (input != null && !input.isBlank()) {
            inputs.addAll(Arrays.asList(input.split("\\R")));
        }

        executeNode(root);
        return output;
    }

    private void tick() {
        if (++steps > MAX_STEPS)
            throw new RuntimeException("Execution Error: Maximum instruction limit exceeded.");
    }

    private void executeNode(ASTNode node) {
        tick();

        switch (node.getType()) {
            case "PROGRAM":
            case "BLOCK":
                for (ASTNode child : node.getChildren()) executeNode(child);
                break;

            case "DECLARATION":
                String name = node.getChildren().get(0).getValue();
                Object value = node.getChildren().size() > 1
                    ? evaluate(node.getChildren().get(1)) : 0;
                variables.put(name, value);
                break;

            case "ASSIGNMENT":
                variables.put(node.getValue(), evaluate(node.getChildren().get(0)));
                break;

            case "PRINT":
                output.add(formatOutput(evaluate(node.getChildren().get(0))));
                break;

            case "IF":
                if (truth(evaluate(node.getChildren().get(0)))) {
                    executeNode(node.getChildren().get(1));
                } else if (node.getChildren().size() > 2) {
                    executeNode(node.getChildren().get(2));
                }
                break;

            case "WHILE":
                while (truth(evaluate(node.getChildren().get(0)))) {
                    tick();
                    executeNode(node.getChildren().get(1));
                }
                break;

            case "FOR":
                executeFor(node);
                break;

            case "EMPTY":
                break;

            default:
                for (ASTNode child : node.getChildren()) executeNode(child);
        }
    }

    private void executeFor(ASTNode node) {
        List<ASTNode> c = node.getChildren();
        executeNode(c.get(0));

        while (c.get(1).getType().equals("EMPTY") ||
               truth(evaluate(c.get(1)))) {
            tick();
            executeNode(c.get(3));

            if (!c.get(2).getType().equals("EMPTY"))
                executeNode(c.get(2));
        }
    }

    private Object evaluate(ASTNode node) {
        switch (node.getType()) {
            case "LITERAL":
                return literal(node.getValue());

            case "IDENTIFIER":
                if (!variables.containsKey(node.getValue()))
                    throw new RuntimeException("Runtime Error: Variable '" +
                        node.getValue() + "' has no value.");
                return variables.get(node.getValue());

            case "UNARY":
                Object operand = evaluate(node.getChildren().get(0));
                switch (node.getValue()) {
                    case "-": return -number(operand);
                    case "+": return number(operand);
                    case "!": return !truth(operand);
                    default: throw new RuntimeException("Unsupported unary operator.");
                }

            case "BINARY":
                Object left = evaluate(node.getChildren().get(0));
                String op = node.getValue();

                if (op.equals("&&") && !truth(left)) return false;
                if (op.equals("||") && truth(left)) return true;

                Object right = evaluate(node.getChildren().get(1));
                double a = number(left);
                double b = number(right);

                switch (op) {
                    case "+": return a + b;
                    case "-": return a - b;
                    case "*": return a * b;
                    case "/":
                        if (b == 0) throw new RuntimeException("Runtime Error: Division by zero.");
                        return a / b;
                    case "%":
                        if (b == 0) throw new RuntimeException("Runtime Error: Division by zero.");
                        return a % b;
                    case "<": return a < b;
                    case ">": return a > b;
                    case "<=": return a <= b;
                    case ">=": return a >= b;
                    case "==": return left.equals(right) || a == b;
                    case "!=": return !(left.equals(right) || a == b);
                    case "&&": return truth(left) && truth(right);
                    case "||": return truth(left) || truth(right);
                    default: throw new RuntimeException("Unsupported operator: " + op);
                }

            default:
                throw new RuntimeException("Unsupported expression: " + node.getType());
        }
    }

    private Object literal(String value) {
        if (value.equals("true")) return true;
        if (value.equals("false")) return false;

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private double number(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof Boolean) return (Boolean) value ? 1 : 0;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Runtime Error: Expected a numeric value.");
        }
    }

    private boolean truth(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return number(value) != 0;
        return value != null;
    }

    private String formatOutput(Object value) {
        if (value instanceof Double number
                && Double.isFinite(number)
                && number == Math.rint(number)
                && Math.abs(number) < 9.0e18) {
            return Long.toString(number.longValue());
        }
        return String.valueOf(value);
    }

}
