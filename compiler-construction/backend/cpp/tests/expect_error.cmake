execute_process(
    COMMAND "${COMPILER}" "${SOURCE}"
    RESULT_VARIABLE compiler_result
    OUTPUT_VARIABLE compiler_output
    ERROR_VARIABLE compiler_error
)

if("${compiler_result}" STREQUAL "0")
    message(FATAL_ERROR "Compiler accepted invalid source: ${SOURCE}")
endif()

string(FIND "${compiler_output}${compiler_error}" "${EXPECTED_ERROR}" error_position)
if(error_position EQUAL -1)
    message(FATAL_ERROR "Compiler did not report '${EXPECTED_ERROR}': ${compiler_output}${compiler_error}")
endif()
