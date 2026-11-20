#include "compiler.h"

#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef enum {
    TYPE_UNKNOWN,
    TYPE_INT,
    TYPE_FLOAT,
    TYPE_BOOL,
    TYPE_CHAR,
    TYPE_STRING,
    TYPE_VOID,
    TYPE_INT_POINTER,
    TYPE_FLOAT_POINTER,
    TYPE_BOOL_POINTER,
    TYPE_CHAR_POINTER,
    TYPE_INT_ARRAY,
    TYPE_FLOAT_ARRAY,
    TYPE_BOOL_ARRAY,
    TYPE_CHAR_ARRAY
} ValueType;

typedef struct Symbol {
    char *name;
    ValueType type;
    int line;
    int depth;
    int function_id;
    struct Symbol *next;
} Symbol;

typedef struct FunctionInfo {
    const AstNode *node;
    char *name;
    ValueType return_type;
    size_t parameter_count;
    struct FunctionInfo *next;
} FunctionInfo;

static Symbol *symbols;
static FunctionInfo *functions;
static int scope_depth;
static int active_function_id;
static ValueType active_return_type;
static bool active_return_seen;

static ValueType expression_type(const AstNode *node,
                                 DiagnosticArray *diagnostics);

static char *duplicate(const char *value) {
    char *copy = malloc(strlen(value) + 1);
    if (copy == NULL) {
        fprintf(stderr, "fatal: out of memory\n");
        exit(EXIT_FAILURE);
    }
    strcpy(copy, value);
    return copy;
}

static const char *type_name(ValueType type) {
    switch (type) {
        case TYPE_INT: return "int";
        case TYPE_FLOAT: return "float";
        case TYPE_BOOL: return "bool";
        case TYPE_CHAR: return "char";
        case TYPE_STRING: return "string";
        case TYPE_VOID: return "void";
        case TYPE_INT_POINTER: return "int*";
        case TYPE_FLOAT_POINTER: return "float*";
        case TYPE_BOOL_POINTER: return "bool*";
        case TYPE_CHAR_POINTER: return "char*";
        case TYPE_INT_ARRAY: return "int[]";
        case TYPE_FLOAT_ARRAY: return "float[]";
        case TYPE_BOOL_ARRAY: return "bool[]";
        case TYPE_CHAR_ARRAY: return "char[]";
        default: return "unknown";
    }
}

static ValueType declared_type(const char *value) {
    if (strcmp(value, "INT") == 0) return TYPE_INT;
    if (strcmp(value, "FLOAT") == 0) return TYPE_FLOAT;
    if (strcmp(value, "BOOL") == 0) return TYPE_BOOL;
    if (strcmp(value, "CHAR") == 0) return TYPE_CHAR;
    if (strcmp(value, "VOID") == 0) return TYPE_VOID;
    if (strcmp(value, "INT*") == 0) return TYPE_INT_POINTER;
    if (strcmp(value, "FLOAT*") == 0) return TYPE_FLOAT_POINTER;
    if (strcmp(value, "BOOL*") == 0) return TYPE_BOOL_POINTER;
    if (strcmp(value, "CHAR*") == 0) return TYPE_CHAR_POINTER;
    if (strcmp(value, "INT[]") == 0) return TYPE_INT_ARRAY;
    if (strcmp(value, "FLOAT[]") == 0) return TYPE_FLOAT_ARRAY;
    if (strcmp(value, "BOOL[]") == 0) return TYPE_BOOL_ARRAY;
    if (strcmp(value, "CHAR[]") == 0) return TYPE_CHAR_ARRAY;
    return TYPE_UNKNOWN;
}

static Symbol *lookup(const char *name) {
    for (Symbol *symbol = symbols; symbol != NULL; symbol = symbol->next) {
        if (symbol->function_id == active_function_id &&
            symbol->depth <= scope_depth &&
            strcmp(symbol->name, name) == 0) {
            return symbol;
        }
    }
    return NULL;
}

static FunctionInfo *find_function(const char *name) {
    for (FunctionInfo *function = functions; function != NULL;
         function = function->next) {
        if (strcmp(function->name, name) == 0) return function;
    }
    return NULL;
}

static void declare_symbol(const AstNode *node,
                           DiagnosticArray *diagnostics) {
    const char *name = node->children[0]->value;
    Symbol *existing = lookup(name);
    if (existing != NULL && existing->depth == scope_depth) {
        add_diagnostic(diagnostics, node->line, 1,
                       "variable '%s' already declared", name);
        return;
    }

    Symbol *symbol = calloc(1, sizeof(*symbol));
    if (symbol == NULL) {
        fprintf(stderr, "fatal: out of memory\n");
        exit(EXIT_FAILURE);
    }
    symbol->name = duplicate(name);
    symbol->type = declared_type(node->value);
    symbol->line = node->line;
    symbol->depth = scope_depth;
    symbol->function_id = active_function_id;
    symbol->next = symbols;
    symbols = symbol;
}

static void declare_parameter(const AstNode *node,
                              DiagnosticArray *diagnostics) {
    AstNode declaration = {
        .kind = AST_DECLARATION,
        .value = node->value,
        .line = node->line,
        .children = node->children,
        .child_count = node->child_count
    };
    declare_symbol(&declaration, diagnostics);
}

static bool compatible(ValueType target, ValueType source) {
    if (target == source) return true;
    if ((target == TYPE_FLOAT || target == TYPE_INT ||
         target == TYPE_CHAR) &&
        (source == TYPE_FLOAT || source == TYPE_INT ||
         source == TYPE_CHAR)) {
        return true;
    }
    if ((target == TYPE_INT_POINTER && source == TYPE_INT_ARRAY) ||
        (target == TYPE_FLOAT_POINTER && source == TYPE_FLOAT_ARRAY) ||
        (target == TYPE_BOOL_POINTER && source == TYPE_BOOL_ARRAY) ||
        (target == TYPE_CHAR_POINTER && source == TYPE_CHAR_ARRAY) ||
        (target == TYPE_INT_ARRAY && source == TYPE_INT_POINTER) ||
        (target == TYPE_FLOAT_ARRAY && source == TYPE_FLOAT_POINTER) ||
        (target == TYPE_BOOL_ARRAY && source == TYPE_BOOL_POINTER) ||
        (target == TYPE_CHAR_ARRAY && source == TYPE_CHAR_POINTER)) {
        return true;
    }
    return false;
}

static bool is_numeric(ValueType type) {
    return type == TYPE_INT || type == TYPE_FLOAT || type == TYPE_CHAR;
}

static bool is_pointer(ValueType type) {
    return type >= TYPE_INT_POINTER && type <= TYPE_CHAR_POINTER;
}

static bool is_array(ValueType type) {
    return type >= TYPE_INT_ARRAY && type <= TYPE_CHAR_ARRAY;
}

static ValueType element_type(ValueType type) {
    switch (type) {
        case TYPE_INT_POINTER:
        case TYPE_INT_ARRAY: return TYPE_INT;
        case TYPE_FLOAT_POINTER:
        case TYPE_FLOAT_ARRAY: return TYPE_FLOAT;
        case TYPE_BOOL_POINTER:
        case TYPE_BOOL_ARRAY: return TYPE_BOOL;
        case TYPE_CHAR_POINTER:
        case TYPE_CHAR_ARRAY: return TYPE_CHAR;
        default: return TYPE_UNKNOWN;
    }
}

static ValueType pointer_type(ValueType type) {
    switch (type) {
        case TYPE_INT: return TYPE_INT_POINTER;
        case TYPE_FLOAT: return TYPE_FLOAT_POINTER;
        case TYPE_BOOL: return TYPE_BOOL_POINTER;
        case TYPE_CHAR: return TYPE_CHAR_POINTER;
        default: return TYPE_UNKNOWN;
    }
}

static ValueType literal_type(const char *value) {
    if (value[0] == '"') return TYPE_STRING;
    if (strcmp(value, "true") == 0 || strcmp(value, "false") == 0) {
        return TYPE_BOOL;
    }
    if (value[0] == '\'') return TYPE_CHAR;
    return strchr(value, '.') == NULL ? TYPE_INT : TYPE_FLOAT;
}

static void check_call(const AstNode *node, DiagnosticArray *diagnostics) {
    FunctionInfo *function = find_function(node->value);
    if (function == NULL) {
        add_diagnostic(diagnostics, node->line, 1,
                       "undeclared function '%s'", node->value);
        for (size_t i = 0; i < node->child_count; i++) {
            expression_type(node->children[i], diagnostics);
        }
        return;
    }

    if (node->child_count != function->parameter_count) {
        add_diagnostic(diagnostics, node->line, 1,
                       "function '%s' expects %zu argument(s), got %zu",
                       node->value, function->parameter_count,
                       node->child_count);
    }

    const AstNode *parameters = function->node->children[0];
    size_t count = node->child_count < parameters->child_count
                       ? node->child_count
                       : parameters->child_count;
    for (size_t i = 0; i < count; i++) {
        ValueType argument = expression_type(node->children[i], diagnostics);
        ValueType parameter = declared_type(parameters->children[i]->value);
        if (argument != TYPE_UNKNOWN && !compatible(parameter, argument)) {
            add_diagnostic(diagnostics, node->children[i]->line, 1,
                           "argument %zu of '%s' expects %s, got %s",
                           i + 1, node->value, type_name(parameter),
                           type_name(argument));
        }
    }
}

static ValueType expression_type(const AstNode *node,
                                 DiagnosticArray *diagnostics) {
    if (node == NULL) return TYPE_UNKNOWN;
    if (node->kind == AST_EMPTY) return TYPE_UNKNOWN;
    if (node->kind == AST_LITERAL) return literal_type(node->value);
    if (node->kind == AST_IDENTIFIER) {
        Symbol *symbol = lookup(node->value);
        if (symbol == NULL) {
            add_diagnostic(diagnostics, node->line, 1,
                           "undeclared variable '%s'", node->value);
            return TYPE_UNKNOWN;
        }
        return symbol->type;
    }
    if (node->kind == AST_CALL) {
        check_call(node, diagnostics);
        FunctionInfo *function = find_function(node->value);
        return function == NULL ? TYPE_UNKNOWN : function->return_type;
    }
    if (node->kind == AST_INDEX) {
        ValueType base = expression_type(node->children[0], diagnostics);
        ValueType index = expression_type(node->children[1], diagnostics);
        if (index != TYPE_INT && index != TYPE_UNKNOWN) {
            add_diagnostic(diagnostics, node->line, 1,
                           "array index must be int");
        }
        if (!is_array(base) && !is_pointer(base) &&
            base != TYPE_UNKNOWN) {
            add_diagnostic(diagnostics, node->line, 1,
                           "subscripted value is not an array or pointer");
            return TYPE_UNKNOWN;
        }
        return element_type(base);
    }
    if (node->kind == AST_INC_DEC) {
        ValueType operand = expression_type(node->children[0], diagnostics);
        if (!is_numeric(operand) && operand != TYPE_UNKNOWN) {
            add_diagnostic(diagnostics, node->line, 1,
                           "increment/decrement requires a numeric variable");
        }
        return operand;
    }
    if (node->kind == AST_UNARY) {
        ValueType operand = expression_type(node->children[0], diagnostics);
        if (strcmp(node->value, "&") == 0) {
            return pointer_type(operand);
        }
        if (strcmp(node->value, "*") == 0) {
            if (!is_pointer(operand) && operand != TYPE_UNKNOWN) {
                add_diagnostic(diagnostics, node->line, 1,
                               "cannot dereference a non-pointer value");
                return TYPE_UNKNOWN;
            }
            return element_type(operand);
        }
        if (strcmp(node->value, "!") == 0) {
            if (operand != TYPE_BOOL && operand != TYPE_UNKNOWN) {
                add_diagnostic(diagnostics, node->line, 1,
                               "operator ! requires a bool operand");
            }
            return TYPE_BOOL;
        }
        if (operand != TYPE_INT && operand != TYPE_FLOAT &&
            operand != TYPE_CHAR &&
            operand != TYPE_UNKNOWN) {
            add_diagnostic(diagnostics, node->line, 1,
                           "unary operator requires a numeric operand");
        }
        return operand;
    }
    if (node->kind == AST_BINARY) {
        ValueType left = expression_type(node->children[0], diagnostics);
        ValueType right = expression_type(node->children[1], diagnostics);
        const char *operator = node->value;
        if (strcmp(operator, "&&") == 0 || strcmp(operator, "||") == 0) {
            if ((left != TYPE_BOOL && left != TYPE_UNKNOWN) ||
                (right != TYPE_BOOL && right != TYPE_UNKNOWN)) {
                add_diagnostic(diagnostics, node->line, 1,
                               "logical operator '%s' requires bool operands",
                               operator);
            }
            return TYPE_BOOL;
        }
        if (strcmp(operator, "==") == 0 || strcmp(operator, "!=") == 0) {
            if (left != TYPE_UNKNOWN && right != TYPE_UNKNOWN &&
                !compatible(left, right) && !compatible(right, left)) {
                add_diagnostic(diagnostics, node->line, 1,
                               "incompatible equality operands");
            }
            return TYPE_BOOL;
        }
        if (strchr("+-*/%", operator[0]) != NULL) {
            if ((!is_numeric(left) && left != TYPE_UNKNOWN) ||
                (!is_numeric(right) && right != TYPE_UNKNOWN)) {
                add_diagnostic(diagnostics, node->line, 1,
                               "arithmetic operator '%s' requires numeric operands",
                               operator);
            }
            if (strcmp(operator, "%") == 0 &&
                (left != TYPE_INT || right != TYPE_INT)) {
                add_diagnostic(diagnostics, node->line, 1,
                               "modulo requires int operands");
            }
            return left == TYPE_FLOAT || right == TYPE_FLOAT
                       ? TYPE_FLOAT
                       : TYPE_INT;
        }
        if (strchr("<>", operator[0]) != NULL) return TYPE_BOOL;
    }
    return TYPE_UNKNOWN;
}

static void analyze_node(const AstNode *node,
                         DiagnosticArray *diagnostics) {
    if (node == NULL) return;
    switch (node->kind) {
        case AST_BLOCK:
            scope_depth++;
            for (size_t i = 0; i < node->child_count; i++) {
                analyze_node(node->children[i], diagnostics);
            }
            scope_depth--;
            break;
        case AST_DECLARATION:
            declare_symbol(node, diagnostics);
            if (node->child_count > 1) {
                ValueType source = expression_type(node->children[1],
                                                   diagnostics);
                ValueType target = declared_type(node->value);
                if (source != TYPE_UNKNOWN && !compatible(target, source)) {
                    add_diagnostic(diagnostics, node->line, 1,
                                   "cannot initialize %s variable with %s value",
                                   type_name(target), type_name(source));
                }
            }
            break;
        case AST_ARRAY_DECL: {
            declare_symbol(node, diagnostics);
            ValueType size = expression_type(node->children[1],
                                             diagnostics);
            if (size != TYPE_INT && size != TYPE_UNKNOWN) {
                add_diagnostic(diagnostics, node->line, 1,
                               "array size must be int");
            }
            break;
        }
        case AST_ASSIGNMENT: {
            Symbol *symbol = lookup(node->value);
            ValueType source = expression_type(node->children[0],
                                               diagnostics);
            if (symbol == NULL) {
                add_diagnostic(diagnostics, node->line, 1,
                               "undeclared variable '%s'", node->value);
            } else if (source != TYPE_UNKNOWN &&
                       !compatible(symbol->type, source)) {
                add_diagnostic(diagnostics, node->line, 1,
                               "cannot assign %s value to %s variable '%s'",
                               type_name(source), type_name(symbol->type),
                               node->value);
            }
            break;
        }
        case AST_COMPOUND_ASSIGN: {
            Symbol *symbol = lookup(node->children[0]->value);
            ValueType source = expression_type(node->children[1],
                                               diagnostics);
            if (symbol == NULL) {
                add_diagnostic(diagnostics, node->line, 1,
                               "undeclared variable '%s'",
                               node->children[0]->value);
            } else if (!is_numeric(symbol->type) || !is_numeric(source)) {
                add_diagnostic(diagnostics, node->line, 1,
                               "compound assignment '%s' requires numeric operands",
                               node->value);
            } else if (node->value[0] == '%' &&
                       (symbol->type != TYPE_INT || source != TYPE_INT)) {
                add_diagnostic(diagnostics, node->line, 1,
                               "modulo assignment requires int operands");
            }
            break;
        }
        case AST_STORE: {
            ValueType target = expression_type(node->children[0],
                                               diagnostics);
            ValueType source = expression_type(node->children[1],
                                               diagnostics);
            if (node->value != NULL) {
                if (!is_numeric(target) || !is_numeric(source)) {
                    add_diagnostic(diagnostics, node->line, 1,
                                   "compound assignment requires numeric operands");
                }
            } else if (source != TYPE_UNKNOWN &&
                       !compatible(target, source)) {
                add_diagnostic(diagnostics, node->line, 1,
                               "incompatible assignment to indexed or dereferenced value");
            }
            break;
        }
        case AST_CALL:
            expression_type(node, diagnostics);
            break;
        case AST_RETURN: {
            active_return_seen = true;
            bool has_value = node->child_count > 0 &&
                             node->children[0]->kind != AST_EMPTY;
            if (!has_value && active_return_type != TYPE_VOID) {
                add_diagnostic(diagnostics, node->line, 1,
                               "non-void function must return a value");
            } else if (has_value && active_return_type == TYPE_VOID) {
                add_diagnostic(diagnostics, node->line, 1,
                               "void function cannot return a value");
            } else if (has_value) {
                ValueType source = expression_type(node->children[0],
                                                   diagnostics);
                if (source != TYPE_UNKNOWN &&
                    !compatible(active_return_type, source)) {
                    add_diagnostic(diagnostics, node->line, 1,
                                   "cannot return %s from %s function",
                                   type_name(source),
                                   type_name(active_return_type));
                }
            }
            break;
        }
        case AST_IF:
        case AST_WHILE:
            if (expression_type(node->children[0], diagnostics) != TYPE_BOOL) {
                add_diagnostic(diagnostics, node->line, 1,
                               "condition must be bool");
            }
            for (size_t i = 1; i < node->child_count; i++) {
                analyze_node(node->children[i], diagnostics);
            }
            break;
        case AST_FOR:
            analyze_node(node->children[0], diagnostics);
            if (node->children[1]->kind != AST_EMPTY &&
                expression_type(node->children[1], diagnostics) != TYPE_BOOL) {
                add_diagnostic(diagnostics, node->line, 1,
                               "for condition must be bool");
            }
            analyze_node(node->children[2], diagnostics);
            analyze_node(node->children[3], diagnostics);
            break;
        case AST_PRINT:
            expression_type(node->children[0], diagnostics);
            break;
        default:
            for (size_t i = 0; i < node->child_count; i++) {
                analyze_node(node->children[i], diagnostics);
            }
            break;
    }
}

static void collect_functions(const AstNode *root,
                              DiagnosticArray *diagnostics) {
    bool has_main = false;
    for (size_t i = 0; i < root->child_count; i++) {
        const AstNode *node = root->children[i];
        if (node->kind != AST_FUNCTION) continue;
        FunctionInfo *existing = find_function(node->value);
        if (existing != NULL) {
            add_diagnostic(diagnostics, node->line, 1,
                           "function '%s' already defined", node->value);
            continue;
        }
        FunctionInfo *function = calloc(1, sizeof(*function));
        if (function == NULL) {
            fprintf(stderr, "fatal: out of memory\n");
            exit(EXIT_FAILURE);
        }
        function->node = node;
        function->name = duplicate(node->value);
        function->return_type = declared_type(node->children[0]->value);
        function->parameter_count = node->children[0]->child_count;
        function->next = functions;
        functions = function;

        if (strcmp(node->value, "main") == 0) {
            has_main = true;
            if (function->return_type != TYPE_INT) {
                add_diagnostic(diagnostics, node->line, 1,
                               "main function must return int");
            }
            if (function->parameter_count != 0) {
                add_diagnostic(diagnostics, node->line, 1,
                               "main function cannot have parameters");
            }
        }
    }
    if (!has_main) {
        add_diagnostic(diagnostics, 1, 1,
                       "program must define an int main function");
    }
}

static void analyze_function(const AstNode *node,
                             int function_id,
                             DiagnosticArray *diagnostics) {
    active_function_id = function_id;
    active_return_type = declared_type(node->children[0]->value);
    active_return_seen = false;
    scope_depth = 0;

    const AstNode *parameters = node->children[0];
    for (size_t i = 0; i < parameters->child_count; i++) {
        declare_parameter(parameters->children[i], diagnostics);
    }
    analyze_node(node->children[1], diagnostics);

    if (active_return_type != TYPE_VOID && !active_return_seen) {
        add_diagnostic(diagnostics, node->line, 1,
                       "non-void function '%s' must return a value",
                       node->value);
    }
}

bool analyze_semantics(const AstNode *root, DiagnosticArray *diagnostics) {
    free_symbols();
    free_functions();
    scope_depth = 0;
    collect_functions(root, diagnostics);

    int function_id = 1;
    for (size_t i = 0; i < root->child_count; i++) {
        if (root->children[i]->kind == AST_FUNCTION) {
            analyze_function(root->children[i], function_id++, diagnostics);
        }
    }
    return diagnostics->count == 0;
}

void print_symbols(void) {
    for (Symbol *symbol = symbols; symbol != NULL; symbol = symbol->next) {
        printf("%s\t%s\tline %d\n", symbol->name,
               type_name(symbol->type), symbol->line);
    }
}

void free_symbols(void) {
    while (symbols != NULL) {
        Symbol *next = symbols->next;
        free(symbols->name);
        free(symbols);
        symbols = next;
    }
}

void free_functions(void) {
    while (functions != NULL) {
        FunctionInfo *next = functions->next;
        free(functions->name);
        free(functions);
        functions = next;
    }
}
