#include "compiler.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct Macro {
    char *name;
    char *value;
    struct Macro *next;
} Macro;

typedef struct {
    bool parent_active;
    bool condition;
    bool saw_else;
} Conditional;

typedef struct {
    char *data;
    size_t length;
    size_t capacity;
} Output;

static char *duplicate(const char *value) {
    char *copy = malloc(strlen(value) + 1);
    if (copy == NULL) {
        fprintf(stderr, "fatal: out of memory\n");
        exit(EXIT_FAILURE);
    }
    strcpy(copy, value);
    return copy;
}

static void append_text(Output *output, const char *text, size_t length) {
    if (output->length + length + 1 > output->capacity) {
        size_t capacity = output->capacity == 0 ? 256 : output->capacity;
        while (capacity < output->length + length + 1) capacity *= 2;
        char *data = realloc(output->data, capacity);
        if (data == NULL) {
            fprintf(stderr, "fatal: out of memory\n");
            exit(EXIT_FAILURE);
        }
        output->data = data;
        output->capacity = capacity;
    }
    memcpy(output->data + output->length, text, length);
    output->length += length;
    output->data[output->length] = '\0';
}

static Macro *find_macro(Macro *macros, const char *name) {
    for (Macro *macro = macros; macro != NULL; macro = macro->next) {
        if (strcmp(macro->name, name) == 0) return macro;
    }
    return NULL;
}

static void define_macro(Macro **macros, const char *name, const char *value) {
    Macro *macro = find_macro(*macros, name);
    if (macro != NULL) {
        free(macro->value);
        macro->value = duplicate(value);
        return;
    }
    macro = calloc(1, sizeof(*macro));
    if (macro == NULL) {
        fprintf(stderr, "fatal: out of memory\n");
        exit(EXIT_FAILURE);
    }
    macro->name = duplicate(name);
    macro->value = duplicate(value);
    macro->next = *macros;
    *macros = macro;
}

static void undefine_macro(Macro **macros, const char *name) {
    Macro **current = macros;
    while (*current != NULL) {
        if (strcmp((*current)->name, name) == 0) {
            Macro *removed = *current;
            *current = removed->next;
            free(removed->name);
            free(removed->value);
            free(removed);
            return;
        }
        current = &(*current)->next;
    }
}

static void free_macros(Macro *macros) {
    while (macros != NULL) {
        Macro *next = macros->next;
        free(macros->name);
        free(macros->value);
        free(macros);
        macros = next;
    }
}

static bool truth_value(Macro *macros, const char *expression) {
    while (isspace((unsigned char)*expression)) expression++;
    if (*expression == '\0' || strcmp(expression, "0") == 0) return false;
    if (strcmp(expression, "1") == 0) return true;
    return find_macro(macros, expression) != NULL;
}

static bool active_state(const Conditional *stack, size_t depth) {
    for (size_t i = 0; i < depth; i++) {
        if (!stack[i].parent_active || !stack[i].condition) {
            return false;
        }
    }
    return true;
}

static const char *skip_space(const char *text) {
    while (isspace((unsigned char)*text)) text++;
    return text;
}

static bool parse_name(const char **cursor, char *name, size_t size) {
    const char *text = skip_space(*cursor);
    if (!isalpha((unsigned char)*text) && *text != '_') return false;
    size_t length = 0;
    while (isalnum((unsigned char)*text) || *text == '_') {
        if (length + 1 < size) name[length++] = *text;
        text++;
    }
    name[length] = '\0';
    *cursor = text;
    return true;
}

static char *read_file(const char *path) {
    FILE *file = fopen(path, "rb");
    if (file == NULL) return NULL;
    if (fseek(file, 0, SEEK_END) != 0) {
        fclose(file);
        return NULL;
    }
    long length = ftell(file);
    if (length < 0 || fseek(file, 0, SEEK_SET) != 0) {
        fclose(file);
        return NULL;
    }
    char *contents = malloc((size_t)length + 1);
    if (contents == NULL) {
        fclose(file);
        return NULL;
    }
    size_t read = fread(contents, 1, (size_t)length, file);
    if (read != (size_t)length && ferror(file)) {
        free(contents);
        fclose(file);
        return NULL;
    }
    contents[read] = '\0';
    fclose(file);
    return contents;
}

static char *include_path(const char *source_path, const char *name) {
    const char *slash = strrchr(source_path, '/');
    if (slash == NULL) return duplicate(name);
    size_t directory_length = (size_t)(slash - source_path + 1);
    char *path = malloc(directory_length + strlen(name) + 1);
    if (path == NULL) {
        fprintf(stderr, "fatal: out of memory\n");
        exit(EXIT_FAILURE);
    }
    memcpy(path, source_path, directory_length);
    strcpy(path + directory_length, name);
    return path;
}

static void expand_line(const char *line, Macro *macros, Output *output,
                        bool *in_block_comment) {
    size_t i = 0;
    while (line[i] != '\0') {
        if (*in_block_comment) {
            if (line[i] == '*' && line[i + 1] == '/') {
                append_text(output, "*/", 2);
                i += 2;
                *in_block_comment = false;
            } else {
                append_text(output, line + i, 1);
                i++;
            }
            continue;
        }
        if (line[i] == '/' && line[i + 1] == '*') {
            append_text(output, "/*", 2);
            i += 2;
            *in_block_comment = true;
            continue;
        }
        if (line[i] == '/' && line[i + 1] == '/') {
            append_text(output, line + i, strlen(line + i));
            return;
        }
        if (line[i] == '"') {
            size_t start = i++;
            while (line[i] != '\0') {
                if (line[i] == '\\' && line[i + 1] != '\0') {
                    i += 2;
                } else if (line[i++] == '"') {
                    break;
                }
            }
            append_text(output, line + start, i - start);
            continue;
        }
        if (line[i] == '\'') {
            size_t start = i++;
            while (line[i] != '\0') {
                if (line[i] == '\\' && line[i + 1] != '\0') {
                    i += 2;
                } else if (line[i++] == '\'') {
                    break;
                }
            }
            append_text(output, line + start, i - start);
            continue;
        }
        if (isalpha((unsigned char)line[i]) || line[i] == '_') {
            size_t start = i++;
            while (isalnum((unsigned char)line[i]) || line[i] == '_') i++;
            char name[128];
            size_t length = i - start;
            if (length >= sizeof(name)) {
                append_text(output, line + start, length);
                continue;
            }
            memcpy(name, line + start, length);
            name[length] = '\0';
            Macro *macro = find_macro(macros, name);
            if (macro == NULL) append_text(output, name, length);
            else append_text(output, macro->value, strlen(macro->value));
            continue;
        }
        append_text(output, line + i, 1);
        i++;
    }
}

static void process_source(const char *source, const char *source_path,
                           Macro **macros, Output *output,
                           DiagnosticArray *diagnostics, int include_depth) {
    if (include_depth > 32) {
        add_diagnostic(diagnostics, 1, 1,
                       "maximum include depth exceeded");
        return;
    }
    Conditional stack[64];
    size_t depth = 0;
    bool in_block_comment = false;
    const char *line_start = source;
    while (*line_start != '\0') {
        const char *line_end = strchr(line_start, '\n');
        size_t line_length = line_end == NULL
                                 ? strlen(line_start)
                                 : (size_t)(line_end - line_start);
        char *line = malloc(line_length + 1);
        if (line == NULL) {
            fprintf(stderr, "fatal: out of memory\n");
            exit(EXIT_FAILURE);
        }
        memcpy(line, line_start, line_length);
        line[line_length] = '\0';
        const char *cursor = skip_space(line);
        bool directive = *cursor == '#' && !in_block_comment;
        bool active = active_state(stack, depth);

        if (directive) {
            cursor = skip_space(cursor + 1);
            char directive_name[32];
            if (!parse_name(&cursor, directive_name,
                            sizeof(directive_name))) {
                add_diagnostic(diagnostics, 1, 1,
                               "invalid preprocessor directive");
            } else if (strcmp(directive_name, "if") == 0 ||
                       strcmp(directive_name, "ifdef") == 0 ||
                       strcmp(directive_name, "ifndef") == 0) {
                if (depth >= 64) {
                    add_diagnostic(diagnostics, 1, 1,
                                   "preprocessor conditional nesting too deep");
                } else {
                    const char *condition = skip_space(cursor);
                    bool value = strcmp(directive_name, "ifdef") == 0
                                     ? find_macro(*macros, condition) != NULL
                                     : strcmp(directive_name, "ifndef") == 0
                                           ? find_macro(*macros, condition) == NULL
                                           : truth_value(*macros, condition);
                    stack[depth++] = (Conditional){
                        active, value, false
                    };
                }
            } else if (strcmp(directive_name, "else") == 0) {
                if (depth == 0 || stack[depth - 1].saw_else) {
                    add_diagnostic(diagnostics, 1, 1,
                                   "unexpected preprocessor #else");
                } else {
                    stack[depth - 1].saw_else = true;
                    stack[depth - 1].condition =
                        !stack[depth - 1].condition;
                }
            } else if (strcmp(directive_name, "endif") == 0) {
                if (depth == 0) {
                    add_diagnostic(diagnostics, 1, 1,
                                   "unexpected preprocessor #endif");
                } else {
                    depth--;
                }
            } else if (active && strcmp(directive_name, "define") == 0) {
                char name[128];
                if (!parse_name(&cursor, name, sizeof(name))) {
                    add_diagnostic(diagnostics, 1, 1,
                                   "expected macro name after #define");
                } else {
                    cursor = skip_space(cursor);
                    define_macro(macros, name,
                                 *cursor == '\0' ? "1" : cursor);
                }
            } else if (active && strcmp(directive_name, "undef") == 0) {
                char name[128];
                if (!parse_name(&cursor, name, sizeof(name))) {
                    add_diagnostic(diagnostics, 1, 1,
                                   "expected macro name after #undef");
                } else {
                    undefine_macro(macros, name);
                }
            } else if (active && strcmp(directive_name, "include") == 0) {
                cursor = skip_space(cursor);
                if (*cursor == '<') {
                    /* System headers are outside this compiler subset. */
                } else if (*cursor == '"') {
                    const char *name = ++cursor;
                    const char *closing = strchr(name, '"');
                    if (closing == NULL) {
                        add_diagnostic(diagnostics, 1, 1,
                                       "unterminated #include path");
                    } else {
                        char *include_name = malloc((size_t)(closing - name) + 1);
                        memcpy(include_name, name, (size_t)(closing - name));
                        include_name[closing - name] = '\0';
                        char *path = include_path(source_path, include_name);
                        char *contents = read_file(path);
                        if (contents == NULL) {
                            add_diagnostic(diagnostics, 1, 1,
                                           "cannot read include file '%s'",
                                           include_name);
                        } else {
                            process_source(contents, path, macros, output,
                                           diagnostics, include_depth + 1);
                            free(contents);
                        }
                        free(path);
                        free(include_name);
                    }
                } else {
                    add_diagnostic(diagnostics, 1, 1,
                                   "expected quoted or system include path");
                }
            } else if (active && strcmp(directive_name, "error") == 0) {
                add_diagnostic(diagnostics, 1, 1,
                               "preprocessor error: %s", skip_space(cursor));
            } else if (active) {
                add_diagnostic(diagnostics, 1, 1,
                               "unsupported preprocessor directive #%s",
                               directive_name);
            }
            append_text(output, "\n", 1);
        } else if (active) {
            expand_line(line, *macros, output, &in_block_comment);
            append_text(output, "\n", 1);
        } else {
            append_text(output, "\n", 1);
        }
        free(line);
        if (line_end == NULL) break;
        line_start = line_end + 1;
    }
    if (depth != 0) {
        add_diagnostic(diagnostics, 1, 1,
                       "unterminated preprocessor conditional");
    }
}

bool preprocess_source(const char *source, const char *source_path,
                       char **processed, DiagnosticArray *diagnostics) {
    Macro *macros = NULL;
    Output output = {0};
    process_source(source, source_path, &macros, &output, diagnostics, 0);
    if (output.data == NULL) {
        output.data = duplicate("");
    }
    *processed = output.data;
    free_macros(macros);
    return diagnostics->count == 0;
}
