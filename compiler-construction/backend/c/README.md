# C compiler backend

This directory contains a standalone C99 compiler frontend for the initial C
subset used by the compiler-construction project.

Supported constructs:

- `int`, `float`, `char`, and `bool` declarations
- Character literals and comma-separated declarations
- Unary `+`, prefix/postfix `++` and `--`, and compound assignments
- Fixed-size arrays and array indexing
- Pointer declarations, address-of, dereference, and pointer parameters
- Object-like macros, local quoted includes, and conditional compilation
- Typed function definitions and parameters
- `int main(void)` program entry point
- Function calls and `return` statements
- assignments and arithmetic/logical expressions
- `if`/`else`, `while`, and `for`
- `printf(expression);`
- `//` and `/* ... */` comments

The preprocessor supports object-like `#define` and `#undef`, quoted local
`#include` files, ignored system includes, and `#if`, `#ifdef`, `#ifndef`,
`#else`, and `#endif`. Function-like macros and full system-header processing
are not implemented.

The pipeline produces tokens, an AST, scoped symbols, three-address code, and
diagnostics. It is intentionally backend-only; the existing React application
and Java API are unchanged.

The Code Optimization phase performs constant folding, local copy propagation,
and removal of redundant/self-copy assignments before TAC generation.

## Build and run

```sh
make
./c-compiler path/to/program.c
```

The compiler returns a non-zero exit status when lexing, parsing, or semantic
diagnostics are reported.

To validate, compile, and run a supported program:

```sh
./c-compiler --run path/to/program.c
```

`--run` uses the system `cc` compiler for the final executable step. The
backend's `printf(expression);` form is converted to a numeric line of output.
The terminal output is labeled for all six compiler-construction phases:
lexical analysis, syntax analysis, semantic analysis, code optimization,
intermediate code generation, and code generation/execution.

## Tests

Run the backend regression tests with:

```sh
make test
```

The tests cover successful compilation, token/AST/symbol/TAC output, lexical
errors, syntax errors, type errors, and block-scope diagnostics.
