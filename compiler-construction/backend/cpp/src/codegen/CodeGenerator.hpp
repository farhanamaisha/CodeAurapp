#pragma once

#include <string>
#include <vector>

namespace compiler_cpp {

class CodeGenerator {
public:
    std::vector<std::string> generate(const std::vector<std::string>& tac);
};

}  // namespace compiler_cpp
