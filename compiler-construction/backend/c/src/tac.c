#include "compiler.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int temporary;
static int label;

static void node(const AstNode *ast);
static void expression(const AstNode *node, char *buffer, size_t size);

static void lvalue_text(const AstNode *node, char *buffer, size_t size) {
    if (node->kind == AST_IDENTIFIER) {
        snprintf(buffer, size, "%s", node->value);
    } else if (node->kind == AST_UNARY &&
               strcmp(node->value, "*") == 0) {
        char operand[128];
        expression(node->children[0], operand, sizeof(operand));
        snprintf(buffer, size, "*%.*s", (int)(size > 1 ? size - 2 : 0),
                 operand);
    } else if (node->kind == AST_INDEX) {
        char base[128];
        char index[128];
        lvalue_text(node->children[0], base, sizeof(base));
        expression(node->children[1], index, sizeof(index));
        int component_size = (int)(size > 3 ? (size - 3) / 2 : 0);
        snprintf(buffer, size, "%.*s[%.*s]", component_size, base,
                 component_size, index);
    } else {
        expression(node, buffer, size);
    }
}

static void call_arguments(const AstNode *node) {
    for (size_t i = 0; i < node->child_count; i++) {
        char argument[128];
        expression(node->children[i], argument, sizeof(argument));
        printf("param %s\n", argument);
    }
}

static void expression(const AstNode *node, char *buffer, size_t size) {
    if (node->kind == AST_LITERAL || node->kind == AST_IDENTIFIER) {
        snprintf(buffer, size, "%s", node->value);
    } else if (node->kind == AST_INC_DEC) {
        char operand[128];
        expression(node->children[0], operand, sizeof(operand));
        char operator = node->value[0] == 'p' && node->value[1] == 'r'
                            ? node->value[3]
                            : node->value[4];
        bool prefix = node->value[0] == 'p' && node->value[1] == 'r' &&
                      node->value[2] == 'e';
        char old_value[128];
        if (prefix) {
            snprintf(buffer, size, "t%d", ++temporary);
            printf("%s = %s %c 1\n", buffer, operand, operator);
            printf("%s = %s\n", node->children[0]->value, buffer);
        } else {
            snprintf(old_value, sizeof(old_value), "t%d", ++temporary);
            printf("%s = %s\n", old_value, operand);
            snprintf(buffer, size, "t%d", ++temporary);
            printf("%s = %s %c 1\n", buffer, operand, operator);
            printf("%s = %s\n", node->children[0]->value, buffer);
            snprintf(buffer, size, "%s", old_value);
        }
    } else if (node->kind == AST_UNARY) {
        char operand[128];
        if (strcmp(node->value, "&") == 0) {
            lvalue_text(node->children[0], operand, sizeof(operand));
        } else {
            expression(node->children[0], operand, sizeof(operand));
        }
        snprintf(buffer, size, "t%d", ++temporary);
        printf("%s = %s%s\n", buffer, node->value, operand);
    } else if (node->kind == AST_BINARY) {
        char left[128], right[128]; expression(node->children[0], left, sizeof(left));
        expression(node->children[1], right, sizeof(right));
        snprintf(buffer, size, "t%d", ++temporary);
        printf("%s = %s %s %s\n", buffer, left, node->value, right);
    } else if (node->kind == AST_INDEX) {
        char base[128];
        char index[128];
        lvalue_text(node->children[0], base, sizeof(base));
        expression(node->children[1], index, sizeof(index));
        snprintf(buffer, size, "t%d", ++temporary);
        printf("%s = %s[%s]\n", buffer, base, index);
    } else if (node->kind == AST_CALL) {
        call_arguments(node);
        snprintf(buffer, size, "t%d", ++temporary);
        printf("%s = call %s, %zu\n", buffer, node->value,
               node->child_count);
    } else {
        if (size > 0) buffer[0] = '\0';
    }
}

static void node(const AstNode *ast) {
    if (ast == NULL) return;
    if (ast->kind == AST_PROGRAM || ast->kind == AST_BLOCK ||
        ast->kind == AST_DECLARATION_LIST) {
        for (size_t i = 0; i < ast->child_count; i++) node(ast->children[i]);
    } else if (ast->kind == AST_DECLARATION && ast->child_count > 1) {
        char value[128]; expression(ast->children[1], value, sizeof(value));
        printf("%s = %s\n", ast->children[0]->value, value);
    } else if (ast->kind == AST_ARRAY_DECL) {
        char size[128];
        expression(ast->children[1], size, sizeof(size));
        printf("array %s, %s\n", ast->children[0]->value, size);
    } else if (ast->kind == AST_ASSIGNMENT) {
        char value[128]; expression(ast->children[0], value, sizeof(value));
        printf("%s = %s\n", ast->value, value);
    } else if (ast->kind == AST_COMPOUND_ASSIGN) {
        char value[128];
        expression(ast->children[1], value, sizeof(value));
        char temporary_name[128];
        snprintf(temporary_name, sizeof(temporary_name), "t%d", ++temporary);
        printf("%s = %s %c %s\n", temporary_name,
               ast->children[0]->value, ast->value[0], value);
        printf("%s = %s\n", ast->children[0]->value, temporary_name);
    } else if (ast->kind == AST_STORE) {
        char target[128];
        char value[128];
        lvalue_text(ast->children[0], target, sizeof(target));
        expression(ast->children[1], value, sizeof(value));
        if (ast->value == NULL) {
            printf("%s = %s\n", target, value);
        } else {
            char temporary_name[128];
            snprintf(temporary_name, sizeof(temporary_name), "t%d",
                     ++temporary);
            printf("%s = %s %c %s\n", temporary_name, target,
                   ast->value[0], value);
            printf("%s = %s\n", target, temporary_name);
        }
    } else if (ast->kind == AST_PRINT) {
        if (ast->value == NULL) {
            char value[128]; expression(ast->children[0], value, sizeof(value));
            printf("print %s\n", value);
        } else {
            printf("print %s", ast->value);
            for (size_t i = 0; i < ast->child_count; i++) {
                char value[128]; expression(ast->children[i], value, sizeof(value));
                printf(", %s", value);
            }
            printf("\n");
        }
    } else if (ast->kind == AST_INPUT) {
        printf("input %s, %s\n", ast->value, ast->children[0]->value);
    } else if (ast->kind == AST_CALL) {
        call_arguments(ast);
        printf("call %s, %zu\n", ast->value, ast->child_count);
    } else if (ast->kind == AST_RETURN) {
        if (ast->child_count == 0 ||
            ast->children[0]->kind == AST_EMPTY) {
            printf("return\n");
        } else {
            char value[128];
            expression(ast->children[0], value, sizeof(value));
            printf("return %s\n", value);
        }
    } else if (ast->kind == AST_IF) {
        char condition[128]; expression(ast->children[0], condition, sizeof(condition));
        int else_label = ++label, end_label = ++label;
        printf("ifFalse %s goto L%d\n", condition, else_label);
        node(ast->children[1]);
        if (ast->child_count > 2) {
            printf("goto L%d\nL%d:\n", end_label, else_label);
            node(ast->children[2]); printf("L%d:\n", end_label);
        } else printf("L%d:\n", else_label);
    } else if (ast->kind == AST_WHILE) {
        int start = ++label, end = ++label;
        printf("L%d:\n", start);
        char condition[128]; expression(ast->children[0], condition, sizeof(condition));
        printf("ifFalse %s goto L%d\n", condition, end);
        node(ast->children[1]); printf("goto L%d\nL%d:\n", start, end);
    } else if (ast->kind == AST_FOR) {
        node(ast->children[0]); int start = ++label, end = ++label;
        printf("L%d:\n", start);
        if (ast->children[1]->kind != AST_EMPTY) {
            char condition[128]; expression(ast->children[1], condition, sizeof(condition));
            printf("ifFalse %s goto L%d\n", condition, end);
        }
        node(ast->children[3]); node(ast->children[2]);
        printf("goto L%d\nL%d:\n", start, end);
    } else if (ast->kind == AST_FUNCTION) {
        printf("\n%s:\n", ast->value);
        node(ast->children[1]);
    }
}

void generate_tac(const AstNode *root) {
    temporary = 0; label = 0; node(root);
}
