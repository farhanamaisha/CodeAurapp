
package runtime;

import ast.ASTNode;
import java.util.*;

public class ProgramExecutor {
    private static final class BreakSignal extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private final Map<String, Object> variables = new LinkedHashMap<>();
    private final List<String> output = new ArrayList<>();

    private Scanner inputScanner;
    private static final int MAX_STEPS = 100000;
    private int steps = 0;

    public List<String> execute(ASTNode root, String input) {
        variables.clear();
        output.clear();
        steps = 0;
        inputScanner = new Scanner(input == null ? "" : input);

        try {
            executeNode(root);
            return output;
        } finally {
            inputScanner.close();
        }
    }

    private void tick() {
        if (++steps > MAX_STEPS) {
            throw new RuntimeException(
                "Runtime Error: Maximum instruction limit exceeded."
            );
        }
    }

    private void executeNode(ASTNode node) {
        if (node == null) return;

        tick();

        switch (node.getType()) {
            case "PROGRAM":
            case "BLOCK":
                for (ASTNode child : node.getChildren()) {
                    executeNode(child);
                }
                break;

            case "SCANNER_DECL":
                // Scanner object is provided by the runtime.
                break;

            case "METHOD_CALL":
                // The runtime owns the input scanner and closes it after execution.
                break;

            case "BREAK":
                throw new BreakSignal();

            case "DECLARATION": {
                String name = node.getChildren().get(0).getValue();
                Object value = node.getChildren().size() > 1
                    ? evaluate(node.getChildren().get(1)) : 0.0;
                variables.put(name, value);
                break;
            }

            case "ARRAY_DECL": {
                String name = node.getChildren().get(0).getValue();
                int size = (int) number(evaluate(node.getChildren().get(1)));
                if (size < 0 || size > MAX_STEPS) {
                    throw new RuntimeException(
                        "Runtime Error: Array size must be between 0 and "
                        + MAX_STEPS + "."
                    );
                }
                variables.put(name, new int[size]);
                break;
            }

            case "ASSIGNMENT":
                variables.put(
                    node.getValue(),
                    evaluate(node.getChildren().get(0))
                );
                break;

            case "ARRAY_ASSIGN": {
                Object value = variables.get(node.getValue());
                if (!(value instanceof int[] array)) {
                    throw new RuntimeException(
                        "Runtime Error: Array '" + node.getValue()
                        + "' has no value."
                    );
                }
                int index = (int) number(evaluate(node.getChildren().get(0)));
                array[index] = (int) number(evaluate(node.getChildren().get(1)));
                break;
            }

            case "PRINT":
                output.add(formatOutput(
                    evaluate(node.getChildren().get(0))
                ));
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

            case "SWITCH":
                executeSwitch(node);
                break;

            case "EMPTY":
                break;

            default:
                for (ASTNode child : node.getChildren()) {
                    executeNode(child);
                }
        }
    }

    private void executeFor(ASTNode node) {
        List<ASTNode> children = node.getChildren();

        executeNode(children.get(0));

        while (children.get(1).getType().equals("EMPTY")
                || truth(evaluate(children.get(1)))) {

            tick();
            executeNode(children.get(3));

            if (!children.get(2).getType().equals("EMPTY")) {
                executeNode(children.get(2));
            }
        }
    }

    private void executeSwitch(ASTNode node) {
        Object selector = evaluate(node.getChildren().get(0));
        int selected = -1;
        int defaultIndex = -1;

        for (int i = 1; i < node.getChildren().size(); i++) {
            ASTNode branch = node.getChildren().get(i);
            if (branch.getType().equals("DEFAULT")) {
                defaultIndex = i;
            } else if (selected < 0
                    && Objects.equals(selector, evaluate(branch.getChildren().get(0)))) {
                selected = i;
            }
        }

        if (selected < 0) selected = defaultIndex;
        if (selected < 0) return;

        try {
            for (int i = selected; i < node.getChildren().size(); i++) {
                ASTNode branch = node.getChildren().get(i);
                ASTNode body = branch.getChildren().get(branch.getChildren().size() - 1);
                executeNode(body);
            }
        } catch (BreakSignal ignored) {
            // A break exits the nearest switch statement.
        }
    }

    private Object evaluate(ASTNode node) {
        switch (node.getType()) {
            case "LITERAL":
                return literal(node.getValue());

            case "IDENTIFIER":
                if (!variables.containsKey(node.getValue())) {
                    throw new RuntimeException(
                        "Runtime Error: Variable '" + node.getValue()
                        + "' has no value."
                    );
                }
                return variables.get(node.getValue());

            case "INPUT":
                return readInput(node.getValue());

            case "CAST": {
                double value = number(evaluate(node.getChildren().get(0)));
                return node.getValue().equals("int") ? (double) ((int) value) : value;
            }

            case "ARRAY_ACCESS": {
                Object value = variables.get(node.getValue());
                if (!(value instanceof int[] array)) {
                    throw new RuntimeException(
                        "Runtime Error: Array '" + node.getValue()
                        + "' has no value."
                    );
                }
                int index = (int) number(evaluate(node.getChildren().get(0)));
                return (double) array[index];
            }

            case "UNARY": {
                Object operand = evaluate(node.getChildren().get(0));

                switch (node.getValue()) {
                    case "-": return -number(operand);
                    case "+": return number(operand);
                    case "!": return !truth(operand);
                    default:
                        throw new RuntimeException(
                            "Runtime Error: Unsupported unary operator."
                        );
                }
            }

            case "BINARY": {
                Object left = evaluate(node.getChildren().get(0));
                String op = node.getValue();

                if (op.equals("&&") && !truth(left)) return false;
                if (op.equals("||") && truth(left)) return true;

                Object right = evaluate(node.getChildren().get(1));

                switch (op) {
                    case "+":
                        if (left instanceof String || right instanceof String) {
                            return String.valueOf(left) + right;
                        }
                        return number(left) + number(right);

                    case "-": return number(left) - number(right);
                    case "*": return number(left) * number(right);

                    case "/":
                        if (number(right) == 0) {
                            throw new RuntimeException(
                                "Runtime Error: Division by zero."
                            );
                        }
                        return number(left) / number(right);

                    case "%":
                        if (number(right) == 0) {
                            throw new RuntimeException(
                                "Runtime Error: Division by zero."
                            );
                        }
                        return number(left) % number(right);

                    case "<": return number(left) < number(right);
                    case ">": return number(left) > number(right);
                    case "<=": return number(left) <= number(right);
                    case ">=": return number(left) >= number(right);
                    case "==": return Objects.equals(left, right);
                    case "!=": return !Objects.equals(left, right);
                    case "&&": return truth(left) && truth(right);
                    case "||": return truth(left) || truth(right);

                    default:
                        throw new RuntimeException(
                            "Runtime Error: Unsupported operator: " + op
                        );
                }
            }

            default:
                throw new RuntimeException(
                    "Runtime Error: Unsupported expression: " + node.getType()
                );
        }
    }

    private Object readInput(String type) {
        try {
            switch (type) {
                case "int":
                    return (double) inputScanner.nextInt();

                case "float":
                    return (double) inputScanner.nextFloat();

                case "double":
                    return inputScanner.nextDouble();

                case "String":
                    return inputScanner.nextLine();

                default:
                    throw new RuntimeException(
                        "Runtime Error: Unsupported Scanner input type: " + type
                    );
            }
        } catch (InputMismatchException e) {
            throw new RuntimeException(
                "Runtime Error: Invalid input. Expected " + type + "."
            );
        } catch (NoSuchElementException e) {
            throw new RuntimeException(
                "Runtime Error: No input available for Scanner.next"
                + type + "()."
            );
        }
    }

    private Object literal(String value) {
        if (value.equals("true")) return true;
        if (value.equals("false")) return false;

        if (value.length() >= 2
                && value.startsWith("\"")
                && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private double number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        if (value instanceof Boolean) {
            return (Boolean) value ? 1 : 0;
        }

        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new RuntimeException(
                "Runtime Error: Expected a numeric value."
            );
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
