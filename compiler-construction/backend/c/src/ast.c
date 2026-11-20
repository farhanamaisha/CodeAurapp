#include "compiler.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static char *duplicate(const char *value) {
    if (value == NULL) return NULL;
    char *copy = malloc(strlen(value) + 1);
    if (copy == NULL) { fprintf(stderr, "fatal: out of memory\n"); exit(EXIT_FAILURE); }
    strcpy(copy, value);
    return copy;
}

AstNode *ast_node(AstKind kind, const char *value, int line) {
    AstNode *node = calloc(1, sizeof(*node));
    if (node == NULL) { fprintf(stderr, "fatal: out of memory\n"); exit(EXIT_FAILURE); }
    node->kind = kind; node->value = duplicate(value); node->line = line;
    return node;
}

void ast_add_child(AstNode *parent, AstNode *child) {
    if (parent->child_count == parent->child_capacity) {
        size_t capacity = parent->child_capacity == 0 ? 4 : parent->child_capacity * 2;
        AstNode **children = realloc(parent->children, capacity * sizeof(*children));
        if (children == NULL) { fprintf(stderr, "fatal: out of memory\n"); exit(EXIT_FAILURE); }
        parent->children = children; parent->child_capacity = capacity;
    }
    parent->children[parent->child_count++] = child;
}

void free_ast(AstNode *node) {
    if (node == NULL) return;
    for (size_t i = 0; i < node->child_count; i++) free_ast(node->children[i]);
    free(node->children); free(node->value); free(node);
}

const char *ast_kind_name(AstKind kind) {
    static const char *names[] = {
        [AST_PROGRAM]="PROGRAM", [AST_BLOCK]="BLOCK",
        [AST_DECLARATION_LIST]="DECLARATION_LIST",
        [AST_DECLARATION]="DECLARATION",
        [AST_ASSIGNMENT]="ASSIGNMENT",
        [AST_COMPOUND_ASSIGN]="COMPOUND_ASSIGN",
        [AST_STORE]="STORE",
        [AST_ARRAY_DECL]="ARRAY_DECL",
        [AST_PRINT]="PRINT", [AST_IF]="IF",
        [AST_INPUT]="INPUT",
        [AST_WHILE]="WHILE", [AST_FOR]="FOR", [AST_FUNCTION]="FUNCTION",
        [AST_PARAMETER_LIST]="PARAMETER_LIST", [AST_PARAMETER]="PARAMETER",
        [AST_CALL]="CALL", [AST_RETURN]="RETURN", [AST_INC_DEC]="INC_DEC",
        [AST_INDEX]="INDEX",
        [AST_BINARY]="BINARY",
        [AST_UNARY]="UNARY", [AST_IDENTIFIER]="IDENTIFIER",
        [AST_LITERAL]="LITERAL", [AST_EMPTY]="EMPTY"
    };
    return names[kind];
}

void print_ast(const AstNode *node, int depth) {
    if (node == NULL) return;
    for (int i = 0; i < depth; i++) printf("  ");
    printf("%s", ast_kind_name(node->kind));
    if (node->value != NULL) printf(": %s", node->value);
    printf(" (line %d)\n", node->line);
    for (size_t i = 0; i < node->child_count; i++) print_ast(node->children[i], depth + 1);
}
