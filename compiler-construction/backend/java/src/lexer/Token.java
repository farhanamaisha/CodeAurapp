
package lexer;

public class Token {

    public enum Type {
        // Keywords
        INT, FLOAT, BOOL,
        IF, ELSE, WHILE, FOR, PRINT,

        // Literals and identifiers
        IDENTIFIER,
        INTEGER_LITERAL,
        FLOAT_LITERAL,
        BOOLEAN_LITERAL,

        // Arithmetic operators
        PLUS, MINUS, MULTIPLY, DIVIDE, MODULO,

        // Assignment and relational operators
        ASSIGN,
        LESS, GREATER,
        LESS_EQUAL, GREATER_EQUAL,
        EQUAL, NOT_EQUAL,

        // Logical operators
        AND, OR, NOT,

        // Delimiters
        LEFT_BRACE, RIGHT_BRACE,
        LEFT_PAREN, RIGHT_PAREN,
        SEMICOLON,

        // Special
        EOF
    }

    private final Type type;
    private final String lexeme;
    private final int line;

    public Token(Type type, String lexeme, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.line = line;
    }

    public Type getType() {
        return type;
    }

    public String getLexeme() {
        return lexeme;
    }

    public int getLine() {
        return line;
    }

    @Override
    public String toString() {
        return "Token{" +
                "type=" + type +
                ", lexeme='" + lexeme + '\'' +
                ", line=" + line +
                '}';
    }
}
