
package lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Lexer {

    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    private int start = 0;
    private int current = 0;
    private int line = 1;

    private static final Map<String, Token.Type> KEYWORDS = Map.of(
        "int", Token.Type.INT,
        "float", Token.Type.FLOAT,
        "bool", Token.Type.BOOL,
        "if", Token.Type.IF,
        "else", Token.Type.ELSE,
        "while", Token.Type.WHILE,
        "for", Token.Type.FOR,
        "print", Token.Type.PRINT,
        "true", Token.Type.BOOLEAN_LITERAL,
        "false", Token.Type.BOOLEAN_LITERAL
    );

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> tokenize() {

        while (!isAtEnd()) {
            start = current;
            scanToken();
        }

        tokens.add(new Token(Token.Type.EOF, "", line));
        return tokens;
    }

    private void scanToken() {
        char c = advance();

        switch (c) {
            case ' ': case '\r': case '\t':
                break;

            case '\n':
                line++;
                break;

            case '+': addToken(Token.Type.PLUS); break;
            case '-': addToken(Token.Type.MINUS); break;
            case '*': addToken(Token.Type.MULTIPLY); break;
            case '%': addToken(Token.Type.MODULO); break;
            case ';': addToken(Token.Type.SEMICOLON); break;
            case '{': addToken(Token.Type.LEFT_BRACE); break;
            case '}': addToken(Token.Type.RIGHT_BRACE); break;
            case '(': addToken(Token.Type.LEFT_PAREN); break;
            case ')': addToken(Token.Type.RIGHT_PAREN); break;

            case '/':
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) {
                        advance();
                    }
                } else {
                    addToken(Token.Type.DIVIDE);
                }
                break;

            case '=':
                addToken(match('=') ? Token.Type.EQUAL : Token.Type.ASSIGN);
                break;

            case '!':
                addToken(match('=') ? Token.Type.NOT_EQUAL : Token.Type.NOT);
                break;

            case '<':
                addToken(match('=') ? Token.Type.LESS_EQUAL : Token.Type.LESS);
                break;

            case '>':
                addToken(match('=') ? Token.Type.GREATER_EQUAL : Token.Type.GREATER);
                break;

            case '&':
                if (match('&')) addToken(Token.Type.AND);
                else throw error("Expected '&' after '&'.");
                break;

            case '|':
                if (match('|')) addToken(Token.Type.OR);
                else throw error("Expected '|' after '|'.");
                break;

            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    throw error("Unexpected character: " + c);
                }
        }
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) {
            advance();
        }

        String text = source.substring(start, current);
        Token.Type type = KEYWORDS.getOrDefault(text, Token.Type.IDENTIFIER);

        addToken(type);
    }

    private void number() {
        while (isDigit(peek())) {
            advance();
        }

        Token.Type type = Token.Type.INTEGER_LITERAL;

        if (peek() == '.' && isDigit(peekNext())) {
            advance();

            while (isDigit(peek())) {
                advance();
            }

            type = Token.Type.FLOAT_LITERAL;
        }

        addToken(type);
    }

    private char advance() {
        return source.charAt(current++);
    }

    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(current) != expected) {
            return false;
        }

        current++;
        return true;
    }

    private char peek() {
        return isAtEnd() ? '\0' : source.charAt(current);
    }

    private char peekNext() {
        return current + 1 >= source.length()
                ? '\0' : source.charAt(current + 1);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private void addToken(Token.Type type) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, line));
    }

    private RuntimeException error(String message) {
        return new RuntimeException(
                "Lexer Error at line " + line + ": " + message
        );
    }
}
