
package semantic;

import ast.ASTNode;
import symboltable.Symbol;
import symboltable.SymbolTable;

import java.util.ArrayList;
import java.util.List;

public class SemanticAnalyzer {

    private SymbolTable symbolTable = new SymbolTable();
    private final List<String> errors = new ArrayList<>();

    public List<String> analyze(ASTNode root) {
        errors.clear();
        symbolTable = new SymbolTable();
        analyzeNode(root);
        return errors;
    }

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    private void analyzeNode(ASTNode node) {

        if (node == null) return;

        switch (node.getType()) {

            case "PROGRAM":
                for (ASTNode child : node.getChildren()) {
                    analyzeNode(child);
                }
                break;

            case "BLOCK":
                symbolTable.enterScope();

                for (ASTNode child : node.getChildren()) {
                    analyzeNode(child);
                }

                symbolTable.exitScope();
                break;

            case "SCANNER_DECL":
                // Scanner initialization is handled by ProgramExecutor.
                break;

            case "DECLARATION":
                declareVariable(node);
                break;

            case "ASSIGNMENT":
                checkAssignment(node);
                break;

            case "PRINT":
                // IMPORTANT: Validate every expression being printed.
                for (ASTNode child : node.getChildren()) {
                    checkExpression(child);
                }
                break;

            case "IF":
            case "WHILE":
                analyzeCondition(node);
                break;

            case "FOR":
                analyzeFor(node);
                break;

            case "EMPTY":
                break;

            default:
                // Validate standalone expression nodes too.
                if (isExpression(node)) {
                    checkExpression(node);
                } else {
                    for (ASTNode child : node.getChildren()) {
                        analyzeNode(child);
                    }
                }
        }
    }

    private boolean isExpression(ASTNode node) {
        String type = node.getType();

        return type.equals("IDENTIFIER")
            || type.equals("LITERAL")
            || type.equals("BINARY")
            || type.equals("UNARY")
            || type.equals("INPUT");
    }

    private void declareVariable(ASTNode node) {

        if (node.getChildren().isEmpty()) return;

        ASTNode identifier = node.getChildren().get(0);

        String variableName = identifier.getValue();
        String variableType = node.getValue();

        Symbol symbol = new Symbol(
            variableName,
            variableType,
            node.getLine()
        );

        if (!symbolTable.declare(symbol)) {
            error(node, "Variable already declared: " + variableName);
            return;
        }

        if (node.getChildren().size() > 1) {

            String expressionType =
                checkExpression(node.getChildren().get(1));

            if (expressionType != null &&
                !isCompatible(variableType, expressionType)) {

                error(node,
                    "Cannot initialize " + variableType
                    + " variable '" + variableName
                    + "' with " + expressionType + " value.");
            }
        }
    }

    private void checkAssignment(ASTNode node) {

        String name = node.getValue();
        Symbol symbol = symbolTable.lookup(name);

        if (symbol == null) {
            error(node, "Undeclared variable: " + name);

            for (ASTNode child : node.getChildren()) {
                checkExpression(child);
            }
            return;
        }

        if (node.getChildren().isEmpty()) return;

        String expressionType =
            checkExpression(node.getChildren().get(0));

        if (expressionType != null &&
            !isCompatible(symbol.getType(), expressionType)) {

            error(node,
                "Cannot assign " + expressionType
                + " value to " + symbol.getType()
                + " variable '" + name + "'.");
        }
    }

    private void analyzeCondition(ASTNode node) {

        if (!node.getChildren().isEmpty()) {

            String conditionType =
                checkExpression(node.getChildren().get(0));

            if (conditionType != null &&
                !conditionType.equals("bool")) {

                error(node, "Condition must be of type bool.");
            }
        }

        for (int i = 1; i < node.getChildren().size(); i++) {
            analyzeNode(node.getChildren().get(i));
        }
    }

    private void analyzeFor(ASTNode node) {

        List<ASTNode> children = node.getChildren();

        if (children.size() < 4) {
            error(node, "Invalid for-loop structure.");
            return;
        }

        symbolTable.enterScope();

        analyzeNode(children.get(0));

        if (!children.get(1).getType().equals("EMPTY")) {

            String conditionType =
                checkExpression(children.get(1));

            if (conditionType != null &&
                !conditionType.equals("bool")) {

                error(node,
                    "For loop condition must be of type bool.");
            }
        }

        if (!children.get(2).getType().equals("EMPTY")) {
            analyzeNode(children.get(2));
        }

        analyzeNode(children.get(3));

        symbolTable.exitScope();
    }

    private String checkExpression(ASTNode node) {

        if (node == null) return null;

        switch (node.getType()) {

            case "LITERAL":
                return getLiteralType(node.getValue());

            case "IDENTIFIER":

                Symbol symbol =
                    symbolTable.lookup(node.getValue());

                if (symbol == null) {
                    error(node,
                        "Variable '" + node.getValue()
                        + "' is not declared.");
                    return null;
                }

                return symbol.getType();

            case "INPUT":
                // Scanner: nextInt(), nextFloat(), nextLine()
                return node.getValue();

            case "UNARY":
                return checkUnaryExpression(node);

            case "BINARY":
                return checkBinaryExpression(node);

            default:
                return null;
        }
    }

    private String checkUnaryExpression(ASTNode node) {

        if (node.getChildren().isEmpty()) return null;

        String operator = node.getValue();

        String operandType =
            checkExpression(node.getChildren().get(0));

        if (operandType == null) return null;

        if (operator.equals("!")) {

            if (!operandType.equals("bool")) {
                error(node,
                    "Logical NOT requires a bool operand.");
                return null;
            }

            return "bool";
        }

        if (operator.equals("-") || operator.equals("+")) {

            if (!isNumeric(operandType)) {
                error(node,
                    "Unary " + operator
                    + " requires a numeric operand.");
                return null;
            }

            return operandType;
        }

        return null;
    }

    private String checkBinaryExpression(ASTNode node) {

        if (node.getChildren().size() < 2) return null;

        String operator = node.getValue();

        String leftType =
            checkExpression(node.getChildren().get(0));

        String rightType =
            checkExpression(node.getChildren().get(1));

        if (leftType == null || rightType == null) return null;

        switch (operator) {

            case "+":
            case "-":
            case "*":
            case "/":
            case "%":

                if (!isNumeric(leftType) || !isNumeric(rightType)) {
                    error(node,
                        "Arithmetic operator '" + operator
                        + "' requires numeric operands.");
                    return null;
                }

                if (operator.equals("%") &&
                    (!leftType.equals("int") ||
                     !rightType.equals("int"))) {

                    error(node,
                        "Modulo operator requires int operands.");
                    return null;
                }

                return leftType.equals("float") ||
                       rightType.equals("float")
                       ? "float" : "int";

            case "<":
            case ">":
            case "<=":
            case ">=":

                if (!isNumeric(leftType) || !isNumeric(rightType)) {
                    error(node,
                        "Comparison operator '" + operator
                        + "' requires numeric operands.");
                    return null;
                }

                return "bool";

            case "==":
            case "!=":

                if (!isCompatible(leftType, rightType) &&
                    !isCompatible(rightType, leftType)) {

                    error(node,
                        "Incompatible types in equality comparison.");
                    return null;
                }

                return "bool";

            case "&&":
            case "||":

                if (!leftType.equals("bool") ||
                    !rightType.equals("bool")) {

                    error(node,
                        "Logical operator '" + operator
                        + "' requires bool operands.");
                    return null;
                }

                return "bool";

            default:
                error(node, "Unknown operator: " + operator);
                return null;
        }
    }

    private String getLiteralType(String value) {

        if (value == null) return null;

        if (value.equals("true") || value.equals("false")) {
            return "bool";
        }

        if (value.length() >= 2 &&
            value.startsWith("\"") &&
            value.endsWith("\"")) {
            return "String";
        }

        if (value.contains(".")) return "float";

        return "int";
    }

    private boolean isNumeric(String type) {
        return type != null &&
            (type.equals("int") || type.equals("float"));
    }

    private boolean isCompatible(String target, String source) {

        if (target == null || source == null) return false;

        if (target.equals(source)) return true;

        return target.equals("float") && source.equals("int");
    }

    private void error(ASTNode node, String message) {
        errors.add("Line " + node.getLine() + ": " + message);
    }
}
