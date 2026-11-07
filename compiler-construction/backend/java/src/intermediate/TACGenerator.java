
package intermediate;

import ast.ASTNode;
import java.util.ArrayList;
import java.util.List;

public class TACGenerator {

    private final List<String> code = new ArrayList<>();
    private int tempCount = 0;
    private int labelCount = 0;

    public List<String> generate(ASTNode root) {
        code.clear();
        tempCount = 0;
        labelCount = 0;

        generateNode(root);
        return code;
    }

    private void generateNode(ASTNode node) {

        switch (node.getType()) {

            case "PROGRAM":
            case "BLOCK":
                for (ASTNode child : node.getChildren()) {
                    generateNode(child);
                }
                break;

            case "DECLARATION":
                if (node.getChildren().size() > 1) {
                    String value = generateExpression(node.getChildren().get(1));
                    code.add(node.getChildren().get(0).getValue() + " = " + value);
                }
                break;

            case "ASSIGNMENT":
                String value = generateExpression(node.getChildren().get(0));
                code.add(node.getValue() + " = " + value);
                break;

            case "PRINT":
                code.add("print " + generateExpression(node.getChildren().get(0)));
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

            default:
                break;
        }
    }

    private void generateIf(ASTNode node) {
        String condition = generateExpression(node.getChildren().get(0));
        String elseLabel = newLabel();
        String endLabel = newLabel();

        code.add("ifFalse " + condition + " goto " + elseLabel);
        generateNode(node.getChildren().get(1));

        if (node.getChildren().size() > 2) {
            code.add("goto " + endLabel);
            code.add(elseLabel + ":");
            generateNode(node.getChildren().get(2));
            code.add(endLabel + ":");
        } else {
            code.add(elseLabel + ":");
        }
    }

    private void generateWhile(ASTNode node) {
        String start = newLabel();
        String end = newLabel();

        code.add(start + ":");
        String condition = generateExpression(node.getChildren().get(0));
        code.add("ifFalse " + condition + " goto " + end);

        generateNode(node.getChildren().get(1));

        code.add("goto " + start);
        code.add(end + ":");
    }

    private void generateFor(ASTNode node) {
        List<ASTNode> children = node.getChildren();

        generateNode(children.get(0));

        String start = newLabel();
        String end = newLabel();

        code.add(start + ":");

        if (!children.get(1).getType().equals("EMPTY")) {
            String condition = generateExpression(children.get(1));
            code.add("ifFalse " + condition + " goto " + end);
        }

        generateNode(children.get(3));

        if (!children.get(2).getType().equals("EMPTY")) {
            generateNode(children.get(2));
        }

        code.add("goto " + start);
        code.add(end + ":");
    }

    private String generateExpression(ASTNode node) {

        switch (node.getType()) {

            case "LITERAL":
            case "IDENTIFIER":
                return node.getValue();

            case "BINARY":
                String left = generateExpression(node.getChildren().get(0));
                String right = generateExpression(node.getChildren().get(1));
                String temp = newTemp();

                code.add(temp + " = " + left + " " +
                         node.getValue() + " " + right);

                return temp;

            case "UNARY":
                String operand = generateExpression(node.getChildren().get(0));
                String result = newTemp();

                code.add(result + " = " + node.getValue() + operand);
                return result;

            default:
                return "";
        }
    }

    private String newTemp() {
        return "t" + (++tempCount);
    }

    private String newLabel() {
        return "L" + (++labelCount);
    }
}
