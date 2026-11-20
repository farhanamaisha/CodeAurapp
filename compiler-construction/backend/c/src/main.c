#define _POSIX_C_SOURCE 200809L

#include "compiler.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>

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
    rewind(file);
    char *source = malloc((size_t)length + 1);
    if (source == NULL) { fclose(file); return NULL; }
    size_t read = fread(source, 1, (size_t)length, file);
    if (read != (size_t)length && ferror(file)) {
        free(source);
        fclose(file);
        return NULL;
    }
    source[read] = '\0';
    fclose(file);
    return source;
}

static void print_diagnostics(const DiagnosticArray *diagnostics) {
    for (size_t i = 0; i < diagnostics->count; i++)
        fprintf(stderr, "Line %d, column %d: %s\n",
                diagnostics->items[i].line, diagnostics->items[i].column,
                diagnostics->items[i].message);
}

static void append_text(char **buffer, size_t *length, size_t *capacity,
                        const char *text, size_t text_length) {
    if (*length + text_length + 1 > *capacity) {
        size_t next = *capacity == 0 ? 256 : *capacity;
        while (next < *length + text_length + 1) next *= 2;
        char *resized = realloc(*buffer, next);
        if (resized == NULL) {
            fprintf(stderr, "fatal: out of memory\n");
            exit(EXIT_FAILURE);
        }
        *buffer = resized;
        *capacity = next;
    }
    memcpy(*buffer + *length, text, text_length);
    *length += text_length;
    (*buffer)[*length] = '\0';
}

static char *runtime_source(const char *source) {
    char *result = NULL;
    size_t length = 0, capacity = 0;
    const char *includes = "#include <stdio.h>\n#include <stdbool.h>\n";
    append_text(&result, &length, &capacity, includes, strlen(includes));
    for (size_t i = 0; source[i] != '\0';) {
        if (strncmp(source + i, "printf(", 7) == 0 &&
            (i == 0 || !(isalnum((unsigned char)source[i - 1]) ||
                          source[i - 1] == '_'))) {
            size_t start = i + 7;
            size_t cursor = start;
            int nesting = 1;
            bool string = false;
            while (source[cursor] != '\0' && nesting > 0) {
                if (source[cursor] == '"' && source[cursor - 1] != '\\')
                    string = !string;
                if (!string) {
                    if (source[cursor] == '(') nesting++;
                    if (source[cursor] == ')') nesting--;
                }
                cursor++;
            }
            if (nesting == 0) {
                const char *prefix = "printf(\"%g\\n\", (double)(";
                append_text(&result, &length, &capacity, prefix,
                            strlen(prefix));
                append_text(&result, &length, &capacity,
                            source + start, cursor - start - 1);
                append_text(&result, &length, &capacity, "))", 2);
                i = cursor;
                continue;
            }
        }
        append_text(&result, &length, &capacity, source + i, 1);
        i++;
    }
    return result;
}

static int run_compiled_source(const char *source) {
    char source_path[64];
    char executable_path[64];
    snprintf(source_path, sizeof(source_path), "/tmp/c-compiler-%ld.c",
             (long)getpid());
    snprintf(executable_path, sizeof(executable_path), "/tmp/c-compiler-%ld",
             (long)getpid());
    FILE *file = fopen(source_path, "wb");
    if (file == NULL) {
        perror(source_path);
        return EXIT_FAILURE;
    }
    size_t length = strlen(source);
    bool written = fwrite(source, 1, length, file) == length;
    fclose(file);
    if (!written) {
        perror(source_path);
        unlink(source_path);
        return EXIT_FAILURE;
    }

    pid_t compiler = fork();
    if (compiler == -1) {
        perror("fork");
        unlink(source_path);
        return EXIT_FAILURE;
    }
    if (compiler == 0) {
        execlp("cc", "cc", "-std=c99", "-x", "c", source_path,
               "-o", executable_path, (char *)NULL);
        perror("cc");
        _exit(127);
    }
    int status;
    waitpid(compiler, &status, 0);
    if (!WIFEXITED(status) || WEXITSTATUS(status) != 0) {
        unlink(source_path);
        unlink(executable_path);
        return EXIT_FAILURE;
    }

    pid_t program = fork();
    if (program == -1) {
        perror("fork");
        unlink(source_path);
        unlink(executable_path);
        return EXIT_FAILURE;
    }
    if (program == 0) {
        execl(executable_path, executable_path, (char *)NULL);
        perror(executable_path);
        _exit(127);
    }
    waitpid(program, &status, 0);
    unlink(source_path);
    unlink(executable_path);
    return WIFEXITED(status) ? WEXITSTATUS(status) : EXIT_FAILURE;
}

int main(int argc, char **argv) {
    bool run = argc == 3 && strcmp(argv[1], "--run") == 0;
    const char *source_path = run ? argv[2] : argc == 2 ? argv[1] : NULL;
    if (source_path == NULL) {
        fprintf(stderr, "Usage: %s [--run] <source.c>\n", argv[0]);
        return EXIT_FAILURE;
    }
    char *source = read_file(source_path);
    if (source == NULL) {
        perror(source_path);
        return EXIT_FAILURE;
    }
    TokenArray tokens = {0};
    DiagnosticArray diagnostics = {0};
    char *processed_source = NULL;
    preprocess_source(source, source_path, &processed_source, &diagnostics);
    if (diagnostics.count == 0)
        lex_source(processed_source, &tokens, &diagnostics);
    printf("=== TOKENS ===\n");
    for (size_t i = 0; i < tokens.count; i++)
        printf("%s\t%s\tline %d\n", token_type_name(tokens.items[i].type),
               tokens.items[i].lexeme, tokens.items[i].line);
    if (diagnostics.count == 0) {
        AstNode *ast = parse_program(&tokens, &diagnostics);
        if (diagnostics.count == 0) {
            printf("\n=== AST ===\n"); print_ast(ast, 0);
            analyze_semantics(ast, &diagnostics);
            if (diagnostics.count == 0) {
                printf("\n=== SYMBOLS ===\n"); print_symbols();
                printf("\n=== THREE-ADDRESS CODE ===\n"); generate_tac(ast);
            }
        }
        free_ast(ast);
    }
    bool success = diagnostics.count == 0;
    if (!success) {
        printf("\n=== DIAGNOSTICS ===\n");
        print_diagnostics(&diagnostics);
    }
    if (success && run) {
        char *generated = runtime_source(processed_source);
        int run_status = run_compiled_source(generated);
        free(generated);
        if (run_status != 0) success = false;
    }
    free_symbols(); free_diagnostics(&diagnostics); free_tokens(&tokens);
    free(processed_source); free(source);
    return success ? EXIT_SUCCESS : EXIT_FAILURE;
}
