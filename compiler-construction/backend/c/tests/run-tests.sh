#!/bin/sh
set -eu

compiler=${1:-./c-compiler}
tests_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT HUP INT TERM

pass_count=0

assert_contains() {
    assert_file=$1
    assert_value=$2
    if ! grep -Fq "$assert_value" "$assert_file"; then
        printf 'Expected "%s" in %s\n' "$assert_value" "$assert_file" >&2
        exit 1
    fi
}

run_valid_test() {
    output="$tmp_dir/valid.out"
    "$compiler" "$tests_dir/valid_program.c" >"$output" 2>"$tmp_dir/valid.err"
    assert_contains "$output" "=== 1. LEXICAL ANALYSIS: TOKENS ==="
    assert_contains "$output" "=== 2. SYNTAX ANALYSIS: AST ==="
    assert_contains "$output" "=== 3. SEMANTIC ANALYSIS: SYMBOLS ==="
    assert_contains "$output" "=== 4. CODE OPTIMIZATION: OPTIMIZED AST ==="
    assert_contains "$output" "=== 5. INTERMEDIATE CODE GENERATION: TAC ==="
    assert_contains "$output" "main:"
    assert_contains "$output" "scanf"
    assert_contains "$output" "sum"
    assert_contains "$output" "return 0"
    assert_contains "$output" "print \"%d\\n\""
    pass_count=$((pass_count + 1))
}

run_language_features_test() {
    output="$tmp_dir/language_features.out"
    "$compiler" "$tests_dir/language_features.c" >"$output" \
        2>"$tmp_dir/language_features.err"
    assert_contains "$output" "CHAR	char"
    assert_contains "$output" "CHAR_LITERAL	'A'"
    assert_contains "$output" "COMPOUND_ASSIGN"
    assert_contains "$output" "t1 = value + other"
    assert_contains "$output" "t3 = value * t2"
    assert_contains "$output" "t4 = value + 1"
    assert_contains "$output" "t5 = value - 1"
    pass_count=$((pass_count + 1))
}

run_arrays_pointers_test() {
    output="$tmp_dir/arrays_pointers.out"
    "$compiler" "$tests_dir/arrays_pointers.c" >"$output" \
        2>"$tmp_dir/arrays_pointers.err"
    assert_contains "$output" "[	["
    assert_contains "$output" "]	]"
    assert_contains "$output" "&	&"
    assert_contains "$output" "array values, 3"
    assert_contains "$output" "values[0]"
    assert_contains "$output" "*pointer = 4"
    assert_contains "$output" "param values"
    pass_count=$((pass_count + 1))
}

run_preprocessor_test() {
    output="$tmp_dir/preprocessor.out"
    "$compiler" "$tests_dir/preprocessor.c" >"$output" \
        2>"$tmp_dir/preprocessor.err"
    assert_contains "$output" "INT_LITERAL	1"
    assert_contains "$output" "INT_LITERAL	3"
    assert_contains "$output" "COMPOUND_ASSIGN"
    assert_contains "$output" "++	++"
    if grep -Fq "INT_LITERAL	99" "$output"; then
        printf 'Inactive preprocessor branch was compiled\n' >&2
        exit 1
    fi
    pass_count=$((pass_count + 1))
}

run_execution_test() {
    output="$tmp_dir/run.out"
    printf '2 3\n' | "$compiler" --run "$tests_dir/valid_program.c" >"$output" \
        2>"$tmp_dir/run.err"
    if ! grep -Fxq "5" "$output"; then
        printf 'Expected executed program output of 5\n' >&2
        exit 1
    fi
    assert_contains "$output" "=== 6. CODE GENERATION AND EXECUTION ==="
    assert_contains "$output" "Status: compiled and executed successfully"
    pass_count=$((pass_count + 1))
}

run_input_test() {
    output="$tmp_dir/input.out"
    printf '42\n' | "$compiler" --run "$tests_dir/input.c" >"$output" \
        2>"$tmp_dir/input.err"
    if ! grep -Fxq "42" "$output"; then
        printf 'Expected scanf program output of 42\n' >&2
        exit 1
    fi
    assert_contains "$output" "input \"%d\", value"
    pass_count=$((pass_count + 1))
}

run_optimization_test() {
    output="$tmp_dir/optimization.out"
    "$compiler" "$tests_dir/optimization.c" >"$output" \
        2>"$tmp_dir/optimization.err"
    assert_contains "$output" "value = 5"
    assert_contains "$output" "copy = value"
    if grep -Fq "copy = copy" "$output"; then
        printf 'Copy-propagated redundant assignment remains\n' >&2
        exit 1
    fi
    pass_count=$((pass_count + 1))
}

run_invalid_test() {
    name=$1
    expected_message=$2
    output="$tmp_dir/$name.out"
    error="$tmp_dir/$name.err"
    if "$compiler" "$tests_dir/$name.c" >"$output" 2>"$error"; then
        printf '%s unexpectedly succeeded\n' "$name" >&2
        exit 1
    fi
    assert_contains "$output" "=== DIAGNOSTICS ==="
    assert_contains "$error" "$expected_message"
    pass_count=$((pass_count + 1))
}

run_valid_test
run_language_features_test
run_arrays_pointers_test
run_preprocessor_test
run_execution_test
run_input_test
run_optimization_test
run_invalid_test invalid_semantic "cannot initialize int variable with bool value"
run_invalid_test invalid_lexical "unexpected character '@'"
run_invalid_test invalid_syntax "expected variable name"
run_invalid_test invalid_scope "undeclared variable 'hidden'"
run_invalid_test invalid_function "function 'get_value' expects 0 argument(s), got 1"
run_invalid_test invalid_char "character literal must contain one character"
run_invalid_test invalid_pointer "incompatible assignment to indexed or dereferenced value"

printf 'C backend tests passed: %d\n' "$pass_count"
