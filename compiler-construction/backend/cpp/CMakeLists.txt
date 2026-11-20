cmake_minimum_required(VERSION 3.16)

project(CppCompilerBackend LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

file(GLOB_RECURSE CPP_SOURCES CONFIGURE_DEPENDS
    ${CMAKE_CURRENT_SOURCE_DIR}/src/*.cpp
)

add_executable(cpp_compiler main.cpp ${CPP_SOURCES})

target_include_directories(cpp_compiler PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

enable_testing()
add_test(NAME cpp_valid_sample COMMAND cpp_compiler ${CMAKE_CURRENT_SOURCE_DIR}/tests/sample_valid.cpp)
set_tests_properties(cpp_valid_sample PROPERTIES PASS_REGULAR_EXPRESSION "\"success\":true")
add_test(NAME cpp_optimized_sample COMMAND cpp_compiler ${CMAKE_CURRENT_SOURCE_DIR}/tests/sample_optimized.cpp)
set_tests_properties(cpp_optimized_sample PROPERTIES PASS_REGULAR_EXPRESSION "t1 = 3")

function(add_invalid_compiler_test name source expected_error)
    add_test(
        NAME ${name}
        COMMAND ${CMAKE_COMMAND}
            -DCOMPILER=$<TARGET_FILE:cpp_compiler>
            -DSOURCE=${CMAKE_CURRENT_SOURCE_DIR}/tests/${source}
            -DEXPECTED_ERROR=${expected_error}
            -P ${CMAKE_CURRENT_SOURCE_DIR}/tests/expect_error.cmake
    )
endfunction()

add_invalid_compiler_test(cpp_invalid_semantic sample_invalid.cpp "Arithmetic operator")
add_invalid_compiler_test(cpp_invalid_lexical sample_lexical_invalid.cpp "Unexpected character")
add_invalid_compiler_test(cpp_invalid_syntax sample_syntax_invalid.cpp "expected ';'")
add_invalid_compiler_test(cpp_invalid_scope sample_scope_invalid.cpp "Undeclared variable")
