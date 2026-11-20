#include "compiler.h"

#include <stdbool.h>
#include <stdio.h>

typedef struct {
    const TokenArray *tokens;
    size_t current;
    DiagnosticArray *diagnostics;
} Parser;

static const Token *peek(Parser *p) { return &p->tokens->items[p->current]; }
static const Token *peek_at(Parser *p, size_t offset) {
    return &p->tokens->items[p->current + offset];
}
static const Token *previous(Parser *p) { return &p->tokens->items[p->current - 1]; }
static bool check(Parser *p, TokenType type) { return peek(p)->type == type; }
static bool at_end(Parser *p) { return check(p, TOKEN_EOF); }
static const Token *advance(Parser *p) {
    if (!at_end(p)) p->current++;
    return previous(p);
}
static bool match(Parser *p, TokenType type) {
    if (!check(p, type)) return false;
    advance(p);
    return true;
}

static bool expect(Parser *p, TokenType type, const char *message) {
    if (match(p, type)) return true;
    add_diagnostic(p->diagnostics, peek(p)->line, peek(p)->column,
                   "%s; found %s", message, token_type_name(peek(p)->type));
    return false;
}

static AstNode *expression(Parser *p);
static AstNode *statement(Parser *p);

static bool is_value_type(TokenType type) {
    return type == TOKEN_INT || type == TOKEN_FLOAT ||
           type == TOKEN_BOOL || type == TOKEN_CHAR;
}

static bool is_return_type(TokenType type) {
    return is_value_type(type) || type == TOKEN_VOID;
}

static AstNode *empty_node(int line) {
    return ast_node(AST_EMPTY, NULL, line);
}

static AstNode *block(Parser *p) {
    AstNode *node = ast_node(AST_BLOCK, NULL, previous(p)->line);
    while (!check(p, TOKEN_RBRACE) && !at_end(p)) {
        ast_add_child(node, statement(p));
    }
    expect(p, TOKEN_RBRACE, "expected '}' after block");
    return node;
}

static AstNode *declaration_item(Parser *p, const Token *type) {
    bool pointer = match(p, TOKEN_STAR);
    if (!expect(p, TOKEN_IDENTIFIER, "expected variable name")) {
        return empty_node(type->line);
    }
    const Token *name = previous(p);
    char type_name[32];
    snprintf(type_name, sizeof(type_name), "%s%s",
             token_type_name(type->type), pointer ? "*" : "");
    AstNode *identifier = ast_node(AST_IDENTIFIER, name->lexeme, name->line);
    if (match(p, TOKEN_LBRACKET)) {
        char array_type[32];
        snprintf(array_type, sizeof(array_type), "%s[]",
                 token_type_name(type->type));
        AstNode *array = ast_node(AST_ARRAY_DECL, array_type,
                                  type->line);
        ast_add_child(array, identifier);
        ast_add_child(array, expression(p));
        expect(p, TOKEN_RBRACKET, "expected ']' after array size");
        return array;
    }
    AstNode *node = ast_node(AST_DECLARATION, type_name, type->line);
    ast_add_child(node, identifier);
    if (match(p, TOKEN_ASSIGN)) ast_add_child(node, expression(p));
    return node;
}

static AstNode *declaration(Parser *p, bool consume_semicolon) {
    const Token *type = advance(p);
    AstNode *first = declaration_item(p, type);
    AstNode *result = first;
    if (match(p, TOKEN_COMMA)) {
        result = ast_node(AST_DECLARATION_LIST, token_type_name(type->type),
                          type->line);
        ast_add_child(result, first);
        do {
            ast_add_child(result, declaration_item(p, type));
        } while (match(p, TOKEN_COMMA));
    }
    if (consume_semicolon) {
        expect(p, TOKEN_SEMICOLON, "expected ';' after declaration");
    }
    return result;
}

static bool is_compound_assignment(TokenType type) {
    return type == TOKEN_PLUS_ASSIGN || type == TOKEN_MINUS_ASSIGN ||
           type == TOKEN_STAR_ASSIGN || type == TOKEN_SLASH_ASSIGN ||
           type == TOKEN_PERCENT_ASSIGN;
}

static AstNode *lvalue(Parser *p) {
    AstNode *target;
    if (match(p, TOKEN_STAR)) {
        AstNode *node = ast_node(AST_UNARY, "*", previous(p)->line);
        ast_add_child(node, lvalue(p));
        target = node;
    } else if (expect(p, TOKEN_IDENTIFIER, "expected assignment target")) {
        const Token *name = previous(p);
        target = ast_node(AST_IDENTIFIER, name->lexeme, name->line);
    } else {
        return empty_node(peek(p)->line);
    }
    while (match(p, TOKEN_LBRACKET)) {
        AstNode *index = ast_node(AST_INDEX, NULL, target->line);
        ast_add_child(index, target);
        ast_add_child(index, expression(p));
        expect(p, TOKEN_RBRACKET, "expected ']' after index");
        target = index;
    }
    return target;
}

static AstNode *assignment(Parser *p, bool consume_semicolon) {
    AstNode *target = lvalue(p);
    const Token *operator = peek(p);
    if (match(p, TOKEN_ASSIGN)) {
    } else if (is_compound_assignment(operator->type)) {
        advance(p);
    } else {
        expect(p, TOKEN_ASSIGN, "expected '='");
    }
    AstKind kind = target->kind == AST_IDENTIFIER
                       ? (is_compound_assignment(operator->type)
                              ? AST_COMPOUND_ASSIGN
                              : AST_ASSIGNMENT)
                       : AST_STORE;
    const char *value = target->kind == AST_IDENTIFIER
                            ? (is_compound_assignment(operator->type)
                                   ? operator->lexeme
                                   : target->value)
                            : (is_compound_assignment(operator->type)
                                   ? operator->lexeme
                                   : NULL);
    AstNode *node = ast_node(kind, value, target->line);
    if (kind == AST_ASSIGNMENT) {
        ast_add_child(node, expression(p));
        free_ast(target);
    } else {
        ast_add_child(node, target);
        ast_add_child(node, expression(p));
    }
    if (consume_semicolon) {
        expect(p, TOKEN_SEMICOLON, "expected ';' after assignment");
    }
    return node;
}

static AstNode *increment(Parser *p) {
    const Token *name = advance(p);
    const Token *operator = advance(p);
    AstNode *node = ast_node(AST_ASSIGNMENT, name->lexeme, name->line);
    AstNode *binary = ast_node(AST_BINARY,
                               operator->type == TOKEN_INCREMENT ? "+" : "-",
                               operator->line);
    ast_add_child(binary, ast_node(AST_IDENTIFIER, name->lexeme, name->line));
    ast_add_child(binary, ast_node(AST_LITERAL, "1", operator->line));
    ast_add_child(node, binary);
    return node;
}

static AstNode *increment_statement(Parser *p, bool prefix) {
    const Token *operator;
    const Token *name;
    if (prefix) {
        operator = advance(p);
        if (!expect(p, TOKEN_IDENTIFIER,
                    "expected variable for increment")) {
            return empty_node(operator->line);
        }
        name = previous(p);
    } else {
        name = advance(p);
        operator = advance(p);
    }
    AstNode *node = ast_node(AST_ASSIGNMENT, name->lexeme, name->line);
    AstNode *binary = ast_node(AST_BINARY,
                               operator->type == TOKEN_INCREMENT ? "+" : "-",
                               operator->line);
    ast_add_child(binary, ast_node(AST_IDENTIFIER, name->lexeme, name->line));
    ast_add_child(binary, ast_node(AST_LITERAL, "1", operator->line));
    ast_add_child(node, binary);
    expect(p, TOKEN_SEMICOLON, "expected ';' after increment");
    return node;
}

static AstNode *call(Parser *p) {
    const Token *name = advance(p);
    AstNode *node = ast_node(AST_CALL, name->lexeme, name->line);
    expect(p, TOKEN_LPAREN, "expected '(' after function name");
    if (!check(p, TOKEN_RPAREN)) {
        do {
            ast_add_child(node, expression(p));
        } while (match(p, TOKEN_COMMA));
    }
    expect(p, TOKEN_RPAREN, "expected ')' after function arguments");
    return node;
}

static AstNode *print_statement(Parser *p) {
    const Token *token = advance(p);
    expect(p, TOKEN_LPAREN, "expected '(' after printf");
    AstNode *node;
    if (check(p, TOKEN_STRING_LITERAL)) {
        const Token *format = advance(p);
        node = ast_node(AST_PRINT, format->lexeme, token->line);
        while (match(p, TOKEN_COMMA)) {
            ast_add_child(node, expression(p));
        }
    } else {
        node = ast_node(AST_PRINT, NULL, token->line);
        ast_add_child(node, expression(p));
    }
    expect(p, TOKEN_RPAREN, "expected ')' after printf argument");
    expect(p, TOKEN_SEMICOLON, "expected ';' after printf");
    return node;
}

static AstNode *input_statement(Parser *p) {
    const Token *token = advance(p);
    expect(p, TOKEN_LPAREN, "expected '(' after scanf");
    if (!expect(p, TOKEN_STRING_LITERAL,
                "expected format string after scanf")) {
        return empty_node(token->line);
    }
    const Token *format = previous(p);
    AstNode *node = ast_node(AST_INPUT, format->lexeme, token->line);
    while (match(p, TOKEN_COMMA)) {
        if (!expect(p, TOKEN_AMPERSAND, "expected '&' before scanf variable")) {
            break;
        }
        if (expect(p, TOKEN_IDENTIFIER, "expected variable after '&'")) {
            ast_add_child(node, ast_node(AST_IDENTIFIER, previous(p)->lexeme,
                                         previous(p)->line));
        }
    }
    expect(p, TOKEN_RPAREN, "expected ')' after scanf argument");
    expect(p, TOKEN_SEMICOLON, "expected ';' after scanf");
    return node;
}

static AstNode *return_statement(Parser *p) {
    const Token *token = advance(p);
    AstNode *node = ast_node(AST_RETURN, NULL, token->line);
    if (check(p, TOKEN_SEMICOLON)) {
        ast_add_child(node, empty_node(token->line));
    } else {
        ast_add_child(node, expression(p));
    }
    expect(p, TOKEN_SEMICOLON, "expected ';' after return");
    return node;
}

static AstNode *if_statement(Parser *p) {
    const Token *token = advance(p);
    expect(p, TOKEN_LPAREN, "expected '(' after if");
    AstNode *node = ast_node(AST_IF, NULL, token->line);
    ast_add_child(node, expression(p));
    expect(p, TOKEN_RPAREN, "expected ')' after condition");
    ast_add_child(node, statement(p));
    if (match(p, TOKEN_ELSE)) ast_add_child(node, statement(p));
    return node;
}

static AstNode *while_statement(Parser *p) {
    const Token *token = advance(p);
    expect(p, TOKEN_LPAREN, "expected '(' after while");
    AstNode *node = ast_node(AST_WHILE, NULL, token->line);
    ast_add_child(node, expression(p));
    expect(p, TOKEN_RPAREN, "expected ')' after condition");
    ast_add_child(node, statement(p));
    return node;
}

static AstNode *for_statement(Parser *p) {
    const Token *token = advance(p);
    expect(p, TOKEN_LPAREN, "expected '(' after for");
    AstNode *node = ast_node(AST_FOR, NULL, token->line);

    if (match(p, TOKEN_SEMICOLON)) {
        ast_add_child(node, empty_node(token->line));
    } else if (is_value_type(peek(p)->type)) {
        ast_add_child(node, declaration(p, false));
        expect(p, TOKEN_SEMICOLON, "expected ';' after for initializer");
    } else if (check(p, TOKEN_IDENTIFIER)) {
        ast_add_child(node, assignment(p, false));
        expect(p, TOKEN_SEMICOLON, "expected ';' after for initializer");
    } else {
        add_diagnostic(p->diagnostics, peek(p)->line, peek(p)->column,
                       "expected for initializer");
        ast_add_child(node, empty_node(peek(p)->line));
        while (!check(p, TOKEN_SEMICOLON) && !at_end(p)) advance(p);
        match(p, TOKEN_SEMICOLON);
    }

    if (check(p, TOKEN_SEMICOLON)) {
        ast_add_child(node, empty_node(peek(p)->line));
    } else {
        ast_add_child(node, expression(p));
    }
    expect(p, TOKEN_SEMICOLON, "expected ';' after for condition");

    if (check(p, TOKEN_RPAREN)) {
        ast_add_child(node, empty_node(peek(p)->line));
    } else if (check(p, TOKEN_INCREMENT) || check(p, TOKEN_DECREMENT)) {
        ast_add_child(node, increment_statement(p, true));
    } else if (check(p, TOKEN_IDENTIFIER) &&
               (peek_at(p, 1)->type == TOKEN_INCREMENT ||
                peek_at(p, 1)->type == TOKEN_DECREMENT)) {
        ast_add_child(node, increment(p));
    } else if (check(p, TOKEN_IDENTIFIER)) {
        ast_add_child(node, assignment(p, false));
    } else {
        add_diagnostic(p->diagnostics, peek(p)->line, peek(p)->column,
                       "expected for update");
        ast_add_child(node, empty_node(peek(p)->line));
        while (!check(p, TOKEN_RPAREN) && !at_end(p)) advance(p);
    }

    expect(p, TOKEN_RPAREN, "expected ')' after for clauses");
    ast_add_child(node, statement(p));
    return node;
}

static AstNode *statement(Parser *p) {
    if (is_value_type(peek(p)->type)) return declaration(p, true);
    if (check(p, TOKEN_PRINTF)) return print_statement(p);
    if (check(p, TOKEN_SCANF)) return input_statement(p);
    if (check(p, TOKEN_RETURN)) return return_statement(p);
    if (check(p, TOKEN_IF)) return if_statement(p);
    if (check(p, TOKEN_WHILE)) return while_statement(p);
    if (check(p, TOKEN_FOR)) return for_statement(p);
    if (match(p, TOKEN_LBRACE)) return block(p);
    if (check(p, TOKEN_INCREMENT) || check(p, TOKEN_DECREMENT)) {
        return increment_statement(p, true);
    }
    if (check(p, TOKEN_IDENTIFIER) || check(p, TOKEN_STAR)) {
        if (peek_at(p, 1)->type == TOKEN_INCREMENT ||
            peek_at(p, 1)->type == TOKEN_DECREMENT) {
            return increment_statement(p, false);
        }
        if (peek_at(p, 1)->type == TOKEN_LPAREN) {
            AstNode *node = call(p);
            expect(p, TOKEN_SEMICOLON, "expected ';' after function call");
            return node;
        }
        return assignment(p, true);
    }

    add_diagnostic(p->diagnostics, peek(p)->line, peek(p)->column,
                   "expected statement; found %s",
                   token_type_name(peek(p)->type));
    const Token *unexpected = advance(p);
    return empty_node(unexpected->line);
}

static AstNode *primary(Parser *p) {
    const Token *token = peek(p);
    if (match(p, TOKEN_IDENTIFIER)) {
        if (check(p, TOKEN_LPAREN)) {
            p->current--;
            return call(p);
        }
        AstNode *identifier = ast_node(AST_IDENTIFIER, token->lexeme,
                                       token->line);
        while (match(p, TOKEN_LBRACKET)) {
            AstNode *index = ast_node(AST_INDEX, NULL, token->line);
            ast_add_child(index, identifier);
            ast_add_child(index, expression(p));
            expect(p, TOKEN_RBRACKET, "expected ']' after index");
            identifier = index;
        }
        if (check(p, TOKEN_INCREMENT) || check(p, TOKEN_DECREMENT)) {
            const Token *operator = advance(p);
            char operation[6] = "post";
            operation[4] = operator->lexeme[0];
            operation[5] = '\0';
            AstNode *node = ast_node(AST_INC_DEC, operation,
                                     operator->line);
            ast_add_child(node, identifier);
            return node;
        }
        return identifier;
    }
    if (match(p, TOKEN_INT_LITERAL) || match(p, TOKEN_FLOAT_LITERAL) ||
        match(p, TOKEN_STRING_LITERAL) || match(p, TOKEN_CHAR_LITERAL) ||
        match(p, TOKEN_BOOL_LITERAL)) {
        return ast_node(AST_LITERAL, token->lexeme, token->line);
    }
    if (match(p, TOKEN_LPAREN)) {
        AstNode *node = expression(p);
        expect(p, TOKEN_RPAREN, "expected ')' after expression");
        return node;
    }

    add_diagnostic(p->diagnostics, token->line, token->column,
                   "expected expression");
    advance(p);
    return empty_node(token->line);
}

static AstNode *unary(Parser *p) {
    if (match(p, TOKEN_NOT) || match(p, TOKEN_MINUS) ||
        match(p, TOKEN_PLUS) || match(p, TOKEN_AMPERSAND) ||
        match(p, TOKEN_STAR)) {
        const Token *operator = previous(p);
        AstNode *node = ast_node(AST_UNARY, operator->lexeme, operator->line);
        ast_add_child(node, unary(p));
        return node;
    }
    if (match(p, TOKEN_INCREMENT) || match(p, TOKEN_DECREMENT)) {
        const Token *operator = previous(p);
        if (!expect(p, TOKEN_IDENTIFIER, "expected variable for increment")) {
            return empty_node(operator->line);
        }
        const Token *name = previous(p);
        char operation[5] = "pre";
        operation[3] = operator->lexeme[0];
        operation[4] = '\0';
        AstNode *node = ast_node(AST_INC_DEC, operation,
                                 operator->line);
        ast_add_child(node, ast_node(AST_IDENTIFIER, name->lexeme,
                                     name->line));
        return node;
    }
    return primary(p);
}

static AstNode *factor(Parser *p) {
    AstNode *left = unary(p);
    while (check(p, TOKEN_STAR) || check(p, TOKEN_SLASH) ||
           check(p, TOKEN_PERCENT)) {
        const Token *operator = advance(p);
        AstNode *node = ast_node(AST_BINARY, operator->lexeme, operator->line);
        ast_add_child(node, left);
        ast_add_child(node, unary(p));
        left = node;
    }
    return left;
}

static AstNode *term(Parser *p) {
    AstNode *left = factor(p);
    while (check(p, TOKEN_PLUS) || check(p, TOKEN_MINUS)) {
        const Token *operator = advance(p);
        AstNode *node = ast_node(AST_BINARY, operator->lexeme, operator->line);
        ast_add_child(node, left);
        ast_add_child(node, factor(p));
        left = node;
    }
    return left;
}

static AstNode *comparison(Parser *p) {
    AstNode *left = term(p);
    while (check(p, TOKEN_LESS) || check(p, TOKEN_LESS_EQUAL) ||
           check(p, TOKEN_GREATER) || check(p, TOKEN_GREATER_EQUAL)) {
        const Token *operator = advance(p);
        AstNode *node = ast_node(AST_BINARY, operator->lexeme, operator->line);
        ast_add_child(node, left);
        ast_add_child(node, term(p));
        left = node;
    }
    return left;
}

static AstNode *equality(Parser *p) {
    AstNode *left = comparison(p);
    while (check(p, TOKEN_EQUAL) || check(p, TOKEN_NOT_EQUAL)) {
        const Token *operator = advance(p);
        AstNode *node = ast_node(AST_BINARY, operator->lexeme, operator->line);
        ast_add_child(node, left);
        ast_add_child(node, comparison(p));
        left = node;
    }
    return left;
}

static AstNode *logical_and(Parser *p) {
    AstNode *left = equality(p);
    while (match(p, TOKEN_AND)) {
        const Token *operator = previous(p);
        AstNode *node = ast_node(AST_BINARY, operator->lexeme, operator->line);
        ast_add_child(node, left);
        ast_add_child(node, equality(p));
        left = node;
    }
    return left;
}

static AstNode *logical_or(Parser *p) {
    AstNode *left = logical_and(p);
    while (match(p, TOKEN_OR)) {
        const Token *operator = previous(p);
        AstNode *node = ast_node(AST_BINARY, operator->lexeme, operator->line);
        ast_add_child(node, left);
        ast_add_child(node, logical_and(p));
        left = node;
    }
    return left;
}

static AstNode *expression(Parser *p) {
    return logical_or(p);
}

static AstNode *function_definition(Parser *p) {
    const Token *return_type = advance(p);
    if (!expect(p, TOKEN_IDENTIFIER, "expected function name")) {
        return empty_node(return_type->line);
    }
    const Token *name = previous(p);
    AstNode *function = ast_node(AST_FUNCTION, name->lexeme, return_type->line);
    AstNode *parameters = ast_node(AST_PARAMETER_LIST,
                                   token_type_name(return_type->type),
                                   return_type->line);
    ast_add_child(function, parameters);

    expect(p, TOKEN_LPAREN, "expected '(' after function name");
    if (check(p, TOKEN_VOID) && peek_at(p, 1)->type == TOKEN_RPAREN) {
        advance(p);
    } else if (!check(p, TOKEN_RPAREN)) {
        do {
            if (!is_value_type(peek(p)->type)) {
                add_diagnostic(p->diagnostics, peek(p)->line, peek(p)->column,
                               "expected parameter type");
                break;
            }
            const Token *type = advance(p);
            bool pointer = match(p, TOKEN_STAR);
            if (!expect(p, TOKEN_IDENTIFIER, "expected parameter name")) break;
            const Token *parameter_name = previous(p);
            bool array = false;
            if (match(p, TOKEN_LBRACKET)) {
                array = true;
                expect(p, TOKEN_RBRACKET,
                       "expected ']' after array parameter");
            }
            char parameter_type[32];
            snprintf(parameter_type, sizeof(parameter_type), "%s%s%s",
                     token_type_name(type->type), pointer ? "*" : "",
                     array ? "[]" : "");
            AstNode *parameter = ast_node(AST_PARAMETER, parameter_type,
                                          type->line);
            ast_add_child(parameter, ast_node(AST_IDENTIFIER,
                                              parameter_name->lexeme,
                                              parameter_name->line));
            ast_add_child(parameters, parameter);
        } while (match(p, TOKEN_COMMA));
    }
    expect(p, TOKEN_RPAREN, "expected ')' after parameters");
    expect(p, TOKEN_LBRACE, "expected '{' before function body");
    ast_add_child(function, block(p));
    return function;
}

AstNode *parse_program(const TokenArray *tokens, DiagnosticArray *diagnostics) {
    Parser parser = {tokens, 0, diagnostics};
    AstNode *program = ast_node(AST_PROGRAM, NULL, 1);
    while (!at_end(&parser)) {
        if (!is_return_type(peek(&parser)->type)) {
            add_diagnostic(diagnostics, peek(&parser)->line,
                           peek(&parser)->column,
                           "expected function return type");
            advance(&parser);
            continue;
        }
        ast_add_child(program, function_definition(&parser));
    }
    return program;
}
