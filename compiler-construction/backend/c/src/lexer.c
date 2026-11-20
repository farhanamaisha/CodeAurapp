#include "compiler.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static char *copy_text(const char *start, size_t length) {
    char *text = malloc(length + 1);
    if (text == NULL) {
        fprintf(stderr, "fatal: out of memory\n");
        exit(EXIT_FAILURE);
    }
    memcpy(text, start, length);
    text[length] = '\0';
    return text;
}

static void add_token(TokenArray *tokens, TokenType type, const char *start,
                      size_t length, int line, int column) {
    if (tokens->count == tokens->capacity) {
        size_t capacity = tokens->capacity == 0 ? 32 : tokens->capacity * 2;
        Token *items = realloc(tokens->items, capacity * sizeof(*items));
        if (items == NULL) {
            fprintf(stderr, "fatal: out of memory\n");
            exit(EXIT_FAILURE);
        }
        tokens->items = items;
        tokens->capacity = capacity;
    }
    tokens->items[tokens->count++] = (Token){
        type, copy_text(start, length), line, column
    };
}

static TokenType keyword_type(const char *text) {
    if (strcmp(text, "int") == 0) return TOKEN_INT;
    if (strcmp(text, "float") == 0) return TOKEN_FLOAT;
    if (strcmp(text, "bool") == 0 || strcmp(text, "_Bool") == 0) return TOKEN_BOOL;
    if (strcmp(text, "char") == 0) return TOKEN_CHAR;
    if (strcmp(text, "void") == 0) return TOKEN_VOID;
    if (strcmp(text, "if") == 0) return TOKEN_IF;
    if (strcmp(text, "else") == 0) return TOKEN_ELSE;
    if (strcmp(text, "while") == 0) return TOKEN_WHILE;
    if (strcmp(text, "for") == 0) return TOKEN_FOR;
    if (strcmp(text, "printf") == 0) return TOKEN_PRINTF;
    if (strcmp(text, "scanf") == 0) return TOKEN_SCANF;
    if (strcmp(text, "return") == 0) return TOKEN_RETURN;
    if (strcmp(text, "true") == 0) return TOKEN_TRUE;
    if (strcmp(text, "false") == 0) return TOKEN_FALSE;
    return TOKEN_IDENTIFIER;
}

bool lex_source(const char *source, TokenArray *tokens, DiagnosticArray *diagnostics) {
    size_t i = 0;
    int line = 1;
    int column = 1;
    while (source[i] != '\0') {
        char c = source[i];
        if (isspace((unsigned char)c)) {
            if (c == '\n') { line++; column = 1; } else column++;
            i++;
            continue;
        }
        if (c == '/' && source[i + 1] == '/') {
            while (source[i] != '\0' && source[i] != '\n') { i++; column++; }
            continue;
        }
        if (c == '/' && source[i + 1] == '*') {
            int start_line = line;
            i += 2; column += 2;
            bool closed = false;
            while (source[i] != '\0') {
                if (source[i] == '*' && source[i + 1] == '/') {
                    i += 2; column += 2; closed = true; break;
                }
                if (source[i] == '\n') { line++; column = 1; i++; }
                else { i++; column++; }
            }
            if (!closed) add_diagnostic(diagnostics, start_line, 1, "unterminated block comment");
            continue;
        }
        int token_line = line;
        int token_column = column;
        if (isalpha((unsigned char)c) || c == '_') {
            size_t start = i;
            while (isalnum((unsigned char)source[i]) || source[i] == '_') { i++; column++; }
            char *text = copy_text(source + start, i - start);
            TokenType type = keyword_type(text);
            if (type == TOKEN_TRUE || type == TOKEN_FALSE) type = TOKEN_BOOL_LITERAL;
            add_token(tokens, type, text, strlen(text), token_line, token_column);
            free(text);
            continue;
        }
        if (isdigit((unsigned char)c)) {
            size_t start = i;
            bool floating = false;
            while (isdigit((unsigned char)source[i])) { i++; column++; }
            if (source[i] == '.' && isdigit((unsigned char)source[i + 1])) {
                floating = true; i++; column++;
                while (isdigit((unsigned char)source[i])) { i++; column++; }
            }
            add_token(tokens, floating ? TOKEN_FLOAT_LITERAL : TOKEN_INT_LITERAL,
                      source + start, i - start, token_line, token_column);
            continue;
        }
        if (c == '"') {
            size_t start = i++;
            column++;
            bool closed = false;
            while (source[i] != '\0') {
                if (source[i] == '\\' && source[i + 1] != '\0') { i += 2; column += 2; continue; }
                if (source[i] == '"') { i++; column++; closed = true; break; }
                if (source[i] == '\n') { line++; column = 1; i++; }
                else { i++; column++; }
            }
            if (!closed) add_diagnostic(diagnostics, token_line, token_column, "unterminated string literal");
            else add_token(tokens, TOKEN_STRING_LITERAL, source + start, i - start, token_line, token_column);
            continue;
        }
        if (c == '\'') {
            size_t start = i++;
            int characters = 0;
            bool closed = false;
            column++;
            while (source[i] != '\0' && source[i] != '\n') {
                if (source[i] == '\\' && source[i + 1] != '\0') {
                    characters++;
                    i += 2;
                    column += 2;
                    continue;
                }
                if (source[i] == '\'') {
                    i++;
                    column++;
                    closed = true;
                    break;
                }
                characters++;
                i++;
                column++;
            }
            if (!closed) {
                add_diagnostic(diagnostics, token_line, token_column,
                               "unterminated character literal");
            } else if (characters != 1) {
                add_diagnostic(diagnostics, token_line, token_column,
                               "character literal must contain one character");
            } else {
                add_token(tokens, TOKEN_CHAR_LITERAL, source + start,
                          i - start, token_line, token_column);
            }
            continue;
        }

        TokenType type = TOKEN_EOF;
        size_t length = 1;
        switch (c) {
            case '+': if (source[i + 1] == '=') { type = TOKEN_PLUS_ASSIGN; length = 2; } else if (source[i + 1] == '+') { type = TOKEN_INCREMENT; length = 2; } else type = TOKEN_PLUS; break;
            case '-': if (source[i + 1] == '=') { type = TOKEN_MINUS_ASSIGN; length = 2; } else if (source[i + 1] == '-') { type = TOKEN_DECREMENT; length = 2; } else type = TOKEN_MINUS; break;
            case '*': if (source[i + 1] == '=') { type = TOKEN_STAR_ASSIGN; length = 2; } else type = TOKEN_STAR; break;
            case '/': if (source[i + 1] == '=') { type = TOKEN_SLASH_ASSIGN; length = 2; } else type = TOKEN_SLASH; break;
            case '%': if (source[i + 1] == '=') { type = TOKEN_PERCENT_ASSIGN; length = 2; } else type = TOKEN_PERCENT; break;
            case '=': type = source[i + 1] == '=' ? TOKEN_EQUAL : TOKEN_ASSIGN; if (type == TOKEN_EQUAL) length = 2; break;
            case '!': type = source[i + 1] == '=' ? TOKEN_NOT_EQUAL : TOKEN_NOT; if (type == TOKEN_NOT_EQUAL) length = 2; break;
            case '<': type = source[i + 1] == '=' ? TOKEN_LESS_EQUAL : TOKEN_LESS; if (type == TOKEN_LESS_EQUAL) length = 2; break;
            case '>': type = source[i + 1] == '=' ? TOKEN_GREATER_EQUAL : TOKEN_GREATER; if (type == TOKEN_GREATER_EQUAL) length = 2; break;
            case '&': if (source[i + 1] == '&') { type = TOKEN_AND; length = 2; } else type = TOKEN_AMPERSAND; break;
            case '|': if (source[i + 1] == '|') { type = TOKEN_OR; length = 2; } break;
            case '(': type = TOKEN_LPAREN; break;
            case ')': type = TOKEN_RPAREN; break;
            case '{': type = TOKEN_LBRACE; break;
            case '}': type = TOKEN_RBRACE; break;
            case '[': type = TOKEN_LBRACKET; break;
            case ']': type = TOKEN_RBRACKET; break;
            case ';': type = TOKEN_SEMICOLON; break;
            case ',': type = TOKEN_COMMA; break;
            default: break;
        }
        if (type == TOKEN_EOF) {
            add_diagnostic(diagnostics, line, column, "unexpected character '%c'", c);
            i++; column++;
        } else {
            add_token(tokens, type, source + i, length, token_line, token_column);
            i += length; column += (int)length;
        }
    }
    add_token(tokens, TOKEN_EOF, "", 0, line, column);
    return diagnostics->count == 0;
}

void free_tokens(TokenArray *tokens) {
    for (size_t i = 0; i < tokens->count; i++) free(tokens->items[i].lexeme);
    free(tokens->items);
    tokens->items = NULL; tokens->count = 0; tokens->capacity = 0;
}

const char *token_type_name(TokenType type) {
    static const char *names[] = {
        [TOKEN_EOF]="EOF", [TOKEN_IDENTIFIER]="IDENTIFIER", [TOKEN_INT_LITERAL]="INT_LITERAL",
        [TOKEN_FLOAT_LITERAL]="FLOAT_LITERAL", [TOKEN_STRING_LITERAL]="STRING_LITERAL",
        [TOKEN_CHAR_LITERAL]="CHAR_LITERAL",
        [TOKEN_BOOL_LITERAL]="BOOL_LITERAL", [TOKEN_INT]="INT", [TOKEN_FLOAT]="FLOAT",
        [TOKEN_BOOL]="BOOL", [TOKEN_CHAR]="CHAR", [TOKEN_VOID]="VOID",
        [TOKEN_IF]="IF", [TOKEN_ELSE]="ELSE",
        [TOKEN_WHILE]="WHILE", [TOKEN_FOR]="FOR", [TOKEN_PRINTF]="PRINTF",
        [TOKEN_SCANF]="SCANF",
        [TOKEN_RETURN]="RETURN", [TOKEN_TRUE]="TRUE", [TOKEN_FALSE]="FALSE",
        [TOKEN_PLUS]="+", [TOKEN_MINUS]="-", [TOKEN_STAR]="*", [TOKEN_SLASH]="/",
        [TOKEN_PERCENT]="%", [TOKEN_ASSIGN]="=", [TOKEN_PLUS_ASSIGN]="+=",
        [TOKEN_MINUS_ASSIGN]="-=", [TOKEN_STAR_ASSIGN]="*=",
        [TOKEN_SLASH_ASSIGN]="/=", [TOKEN_PERCENT_ASSIGN]="%=",
        [TOKEN_EQUAL]="==", [TOKEN_NOT_EQUAL]="!=",
        [TOKEN_LESS]="<", [TOKEN_LESS_EQUAL]="<=", [TOKEN_GREATER]=">", [TOKEN_GREATER_EQUAL]=">=",
        [TOKEN_AND]="&&", [TOKEN_OR]="||", [TOKEN_NOT]="!",
        [TOKEN_AMPERSAND]="&", [TOKEN_INCREMENT]="++",
        [TOKEN_DECREMENT]="--", [TOKEN_LPAREN]="(", [TOKEN_RPAREN]=")",
        [TOKEN_LBRACE]="{", [TOKEN_RBRACE]="}", [TOKEN_LBRACKET]="[",
        [TOKEN_RBRACKET]="]", [TOKEN_SEMICOLON]=";", [TOKEN_COMMA]=","
    };
    return names[type] == NULL ? "UNKNOWN" : names[type];
}
