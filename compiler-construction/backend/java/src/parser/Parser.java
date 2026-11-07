
package parser;

import ast.ASTNode;
import lexer.Token;
import java.util.List;

public class Parser {

    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public ASTNode parse() {
        ASTNode program = node("PROGRAM", null);
        while (!isAtEnd()) {
            program.addChild(statement());
        }
        return program;
    }

    private ASTNode statement() {

        if (match(Token.Type.INT, Token.Type.FLOAT, Token.Type.BOOL))
            return declaration();

        if (match(Token.Type.PRINT))
            return printStatement();

        if (match(Token.Type.IF))
            return ifStatement();

        if (match(Token.Type.WHILE))
            return whileStatement();

        if (match(Token.Type.FOR))
            return forStatement();

        if (match(Token.Type.LEFT_BRACE))
            return block();

        if (check(Token.Type.IDENTIFIER))
            return assignment(true);

        throw error("Unexpected token: " + peek().getLexeme());
    }

    private ASTNode declaration() {
        Token type = previous();
        Token name = consume(Token.Type.IDENTIFIER, "Expected variable name.");

        ASTNode declaration = node("DECLARATION", type.getLexeme());
        declaration.addChild(node("IDENTIFIER", name.getLexeme()));

        if (match(Token.Type.ASSIGN)) {
            declaration.addChild(expression());
        }

        consume(Token.Type.SEMICOLON, "Expected ';' after declaration.");
        return declaration;
    }

    private ASTNode assignment(boolean semicolon) {
        Token name = consume(Token.Type.IDENTIFIER, "Expected variable name.");
        consume(Token.Type.ASSIGN, "Expected '='.");

        ASTNode assign = node("ASSIGNMENT", name.getLexeme());
        assign.addChild(expression());

        if (semicolon)
            consume(Token.Type.SEMICOLON, "Expected ';' after assignment.");

        return assign;
    }

    private ASTNode printStatement() {
        consume(Token.Type.LEFT_PAREN, "Expected '(' after print.");
        ASTNode print = node("PRINT", null);
        print.addChild(expression());
        consume(Token.Type.RIGHT_PAREN, "Expected ')'.");
        consume(Token.Type.SEMICOLON, "Expected ';'.");
        return print;
    }

    private ASTNode ifStatement() {
        consume(Token.Type.LEFT_PAREN, "Expected '(' after if.");

        ASTNode result = node("IF", null);
        result.addChild(expression());

        consume(Token.Type.RIGHT_PAREN, "Expected ')' after condition.");
        result.addChild(statement());

        if (match(Token.Type.ELSE)) {
            result.addChild(statement());
        }

        return result;
    }

    private ASTNode whileStatement() {
        consume(Token.Type.LEFT_PAREN, "Expected '(' after while.");

        ASTNode result = node("WHILE", null);
        result.addChild(expression());

        consume(Token.Type.RIGHT_PAREN, "Expected ')'.");
        result.addChild(statement());

        return result;
    }

    private ASTNode forStatement() {
        consume(Token.Type.LEFT_PAREN, "Expected '(' after for.");

        ASTNode result = node("FOR", null);

        // Initialization
        if (match(Token.Type.SEMICOLON)) {
            result.addChild(node("EMPTY", null));
        } else if (match(Token.Type.INT, Token.Type.FLOAT, Token.Type.BOOL)) {
            Token type = previous();
            Token name = consume(Token.Type.IDENTIFIER, "Expected variable name.");

            ASTNode init = node("DECLARATION", type.getLexeme());
            init.addChild(node("IDENTIFIER", name.getLexeme()));

            if (match(Token.Type.ASSIGN))
                init.addChild(expression());

            result.addChild(init);
            consume(Token.Type.SEMICOLON, "Expected ';' after initialization.");
        } else {
            result.addChild(assignment(false));
            consume(Token.Type.SEMICOLON, "Expected ';' after initialization.");
        }

        // Condition
        if (check(Token.Type.SEMICOLON)) {
            result.addChild(node("EMPTY", null));
        } else {
            result.addChild(expression());
        }

        consume(Token.Type.SEMICOLON, "Expected ';' after condition.");

        // Update
        if (check(Token.Type.RIGHT_PAREN)) {
            result.addChild(node("EMPTY", null));
        } else {
            result.addChild(assignment(false));
        }

        consume(Token.Type.RIGHT_PAREN, "Expected ')' after for clauses.");

        // Loop body
        result.addChild(statement());

        return result;
    }

    private ASTNode block() {
        ASTNode result = node("BLOCK", null);

        while (!check(Token.Type.RIGHT_BRACE) && !isAtEnd()) {
            result.addChild(statement());
        }

        consume(Token.Type.RIGHT_BRACE, "Expected '}' after block.");
        return result;
    }

    private ASTNode expression() {
        return logicalOr();
    }

    private ASTNode logicalOr() {
        ASTNode left = logicalAnd();

        while (match(Token.Type.OR)) {
            Token operator = previous();
            ASTNode right = logicalAnd();
            left = binary(operator, left, right);
        }

        return left;
    }

    private ASTNode logicalAnd() {
        ASTNode left = equality();

        while (match(Token.Type.AND)) {
            Token operator = previous();
            ASTNode right = equality();
            left = binary(operator, left, right);
        }

        return left;
    }

    private ASTNode equality() {
        ASTNode left = comparison();

        while (match(Token.Type.EQUAL, Token.Type.NOT_EQUAL)) {
            Token operator = previous();
            ASTNode right = comparison();
            left = binary(operator, left, right);
        }

        return left;
    }

    private ASTNode comparison() {
        ASTNode left = term();

        while (match(Token.Type.LESS, Token.Type.GREATER,
                Token.Type.LESS_EQUAL, Token.Type.GREATER_EQUAL)) {
            Token operator = previous();
            ASTNode right = term();
            left = binary(operator, left, right);
        }

        return left;
    }

    private ASTNode term() {
        ASTNode left = factor();

        while (match(Token.Type.PLUS, Token.Type.MINUS)) {
            Token operator = previous();
            ASTNode right = factor();
            left = binary(operator, left, right);
        }

        return left;
    }

    private ASTNode factor() {
        ASTNode left = unary();

        while (match(Token.Type.MULTIPLY, Token.Type.DIVIDE, Token.Type.MODULO)) {
            Token operator = previous();
            ASTNode right = unary();
            left = binary(operator, left, right);
        }

        return left;
    }

    private ASTNode unary() {
        if (match(Token.Type.NOT, Token.Type.MINUS)) {
            Token operator = previous();
            ASTNode result = node("UNARY", operator.getLexeme());
            result.addChild(unary());
            return result;
        }

        return primary();
    }

    private ASTNode primary() {
        if (match(Token.Type.INTEGER_LITERAL, Token.Type.FLOAT_LITERAL,
                Token.Type.BOOLEAN_LITERAL)) {
            return node("LITERAL", previous().getLexeme());
        }

        if (match(Token.Type.IDENTIFIER)) {
            return node("IDENTIFIER", previous().getLexeme());
        }

        if (match(Token.Type.LEFT_PAREN)) {
            ASTNode result = expression();
            consume(Token.Type.RIGHT_PAREN, "Expected ')'.");
            return result;
        }

        throw error("Expected an expression.");
    }

    private ASTNode binary(Token operator, ASTNode left, ASTNode right) {
        ASTNode result = node("BINARY", operator.getLexeme());
        result.addChild(left);
        result.addChild(right);
        return result;
    }

    private ASTNode node(String type, String value) {
        return new ASTNode(type, value, previousLine());
    }

    private int previousLine() {
        return current > 0 ? previous().getLine() : peek().getLine();
    }

    private boolean match(Token.Type... types) {
        for (Token.Type type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(Token.Type type) {
        return !isAtEnd() && peek().getType() == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().getType() == Token.Type.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private Token consume(Token.Type type, String message) {
        if (check(type)) return advance();
        throw error(message);
    }

    private RuntimeException error(String message) {
        return new RuntimeException(
            "Parser Error at line " + peek().getLine() + ": " + message
        );
    }
}
