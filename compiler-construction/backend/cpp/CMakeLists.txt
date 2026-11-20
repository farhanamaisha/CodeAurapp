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
add_test(NAME cpp_invalid_sample COMMAND cpp_compiler ${CMAKE_CURRENT_SOURCE_DIR}/tests/sample_invalid.cpp)
set_tests_properties(cpp_invalid_sample PROPERTIES WILL_FAIL TRUE)
