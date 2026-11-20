#include "compiler.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static char *copy_text(const char *value) {
    char *copy = malloc(strlen(value) + 1);
    if (copy == NULL) {
        fprintf(stderr, "fatal: out of memory\n");
        exit(EXIT_FAILURE);
    }
    strcpy(copy, value);
    return copy;
}

static bool integer_literal(const char *value, long *result) {
    if (value == NULL || value[0] == '\'' || value[0] == '"') return false;
    char *end = NULL;
    *result = strtol(value, &end, 10);
    return end != value && *end == '\0';
}

static bool boolean_literal(const char *value, bool *result) {
    if (strcmp(value, "true") == 0) {
        *result = true;
        return true;
    }
    if (strcmp(value, "false") == 0) {
        *result = false;
        return true;
    }
    return false;
}

static void replace_literal(AstNode *node, const char *value) {
    free(node->value);
    node->value = copy_text(value);
    node->kind = AST_LITERAL;
    for (size_t i = 0; i < node->child_count; i++) free_ast(node->children[i]);
    free(node->children);
    node->children = NULL;
    node->child_count = 0;
    node->child_capacity = 0;
}

static void fold_node(AstNode *node) {
    if (node == NULL) return;
    for (size_t i = 0; i < node->child_count; i++) {
        fold_node(node->children[i]);
    }
    if (node->kind == AST_UNARY && node->child_count == 1 &&
        node->children[0]->kind == AST_LITERAL) {
        const char *value = node->children[0]->value;
        long number;
        bool boolean;
        char result[64];
        if (strcmp(node->value, "+") == 0 && integer_literal(value, &number)) {
            snprintf(result, sizeof(result), "%ld", number);
            replace_literal(node, result);
        } else if (strcmp(node->value, "-") == 0 &&
                   integer_literal(value, &number)) {
            snprintf(result, sizeof(result), "%ld", -number);
            replace_literal(node, result);
        } else if (strcmp(node->value, "!") == 0 &&
                   boolean_literal(value, &boolean)) {
            replace_literal(node, boolean ? "false" : "true");
        }
    }
    if (node->kind == AST_BINARY && node->child_count == 2 &&
        node->children[0]->kind == AST_LITERAL &&
        node->children[1]->kind == AST_LITERAL) {
        long left, right;
        if (integer_literal(node->children[0]->value, &left) &&
            integer_literal(node->children[1]->value, &right)) {
            long value = 0;
            bool calculated = true;
            if (strcmp(node->value, "+") == 0) value = left + right;
            else if (strcmp(node->value, "-") == 0) value = left - right;
            else if (strcmp(node->value, "*") == 0) value = left * right;
            else if (strcmp(node->value, "/") == 0) {
                if (right == 0) calculated = false;
                else value = left / right;
            } else if (strcmp(node->value, "%") == 0) {
                if (right == 0) calculated = false;
                else value = left % right;
            } else if (strcmp(node->value, "<") == 0) {
                replace_literal(node, left < right ? "true" : "false");
                return;
            } else if (strcmp(node->value, "<=") == 0) {
                replace_literal(node, left <= right ? "true" : "false");
                return;
            } else if (strcmp(node->value, ">") == 0) {
                replace_literal(node, left > right ? "true" : "false");
                return;
            } else if (strcmp(node->value, ">=") == 0) {
                replace_literal(node, left >= right ? "true" : "false");
                return;
            } else if (strcmp(node->value, "==") == 0) {
                replace_literal(node, left == right ? "true" : "false");
                return;
            } else if (strcmp(node->value, "!=") == 0) {
                replace_literal(node, left != right ? "true" : "false");
                return;
            } else calculated = false;
            if (calculated) {
                char result[64];
                snprintf(result, sizeof(result), "%ld", value);
                replace_literal(node, result);
            }
        }
    }
}

typedef struct Copy {
    char *name;
    char *source;
    struct Copy *next;
} Copy;

static Copy *find_copy(Copy *copies, const char *name) {
    for (Copy *copy = copies; copy != NULL; copy = copy->next) {
        if (strcmp(copy->name, name) == 0) return copy;
    }
    return NULL;
}

static void set_copy(Copy **copies, const char *name, const char *source) {
    Copy *copy = find_copy(*copies, name);
    if (copy != NULL) {
        free(copy->source);
        copy->source = copy_text(source);
        return;
    }
    copy = calloc(1, sizeof(*copy));
    if (copy == NULL) {
        fprintf(stderr, "fatal: out of memory\n");
        exit(EXIT_FAILURE);
    }
    copy->name = copy_text(name);
    copy->source = copy_text(source);
    copy->next = *copies;
    *copies = copy;
}

static void invalidate_copy(Copy **copies, const char *name) {
    Copy **current = copies;
    while (*current != NULL) {
        if (strcmp((*current)->name, name) == 0) {
            Copy *removed = *current;
            *current = removed->next;
            free(removed->name);
            free(removed->source);
            free(removed);
            return;
        }
        current = &(*current)->next;
    }
}

static void free_copies(Copy *copies) {
    while (copies != NULL) {
        Copy *next = copies->next;
        free(copies->name);
        free(copies->source);
        free(copies);
        copies = next;
    }
}

static void propagate_node(AstNode *node, Copy **copies) {
    if (node == NULL) return;
    if (node->kind == AST_IDENTIFIER) {
        Copy *copy = find_copy(*copies, node->value);
        if (copy != NULL) {
            free(node->value);
            node->value = copy_text(copy->source);
        }
        return;
    }
    for (size_t i = 0; i < node->child_count; i++) {
        propagate_node(node->children[i], copies);
    }
}

static void eliminate_dead_assignments(AstNode *block) {
    if (block == NULL || block->kind != AST_BLOCK) return;
    size_t read = 0;
    for (size_t write = 0; write < block->child_count; write++) {
        AstNode *node = block->children[write];
        if (node->kind == AST_ASSIGNMENT &&
            node->children[0]->kind == AST_IDENTIFIER &&
            strcmp(node->value, node->children[0]->value) == 0) {
            free_ast(node);
            continue;
        }
        block->children[read++] = node;
    }
    block->child_count = read;
}

static void optimize_block(AstNode *block) {
    if (block == NULL) return;
    Copy *copies = NULL;
    for (size_t i = 0; i < block->child_count; i++) {
        AstNode *node = block->children[i];
        if (node->kind == AST_ASSIGNMENT &&
            node->children[0]->kind == AST_IDENTIFIER &&
            strcmp(node->value, node->children[0]->value) == 0) {
            free_ast(node);
            for (size_t j = i + 1; j < block->child_count; j++) {
                block->children[j - 1] = block->children[j];
            }
            block->child_count--;
            i--;
            continue;
        }
        if (node->kind == AST_DECLARATION && node->child_count > 1) {
            propagate_node(node->children[1], &copies);
        } else if (node->kind == AST_COMPOUND_ASSIGN &&
                   node->child_count > 1) {
            propagate_node(node->children[1], &copies);
        } else if (node->kind == AST_STORE &&
                   node->child_count > 1) {
            propagate_node(node->children[1], &copies);
        } else {
            propagate_node(node, &copies);
        }
        if (node->kind == AST_DECLARATION && node->child_count > 1) {
            AstNode *initializer = node->children[1];
            if (initializer->kind == AST_IDENTIFIER) {
                set_copy(&copies, node->children[0]->value,
                         initializer->value);
            } else {
                invalidate_copy(&copies, node->children[0]->value);
            }
        } else if (node->kind == AST_ASSIGNMENT) {
            AstNode *rhs = node->children[0];
            invalidate_copy(&copies, node->value);
            if (rhs->kind == AST_IDENTIFIER) {
                set_copy(&copies, node->value, rhs->value);
            }
        } else if (node->kind == AST_BLOCK) {
            optimize_block(node);
        } else if (node->kind == AST_IF || node->kind == AST_WHILE ||
                   node->kind == AST_FOR) {
            for (size_t j = 1; j < node->child_count; j++) {
                optimize_block(node->children[j]);
            }
        }
    }
    free_copies(copies);
    eliminate_dead_assignments(block);
}

void optimize_ast(AstNode *root) {
    if (root == NULL) return;
    fold_node(root);
    if (root->kind == AST_PROGRAM) {
        for (size_t i = 0; i < root->child_count; i++) {
            AstNode *function = root->children[i];
            if (function->kind == AST_FUNCTION &&
                function->child_count > 1) {
                optimize_block(function->children[1]);
            }
        }
    } else {
        optimize_block(root);
    }
}
