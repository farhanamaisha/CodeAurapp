#pragma once

#include <string>
#include <vector>

namespace compiler_cpp {

class TACOptimizer {
public:
    std::vector<std::string> optimize(const std::vector<std::string>& tac);
};

}  // namespace compiler_cpp
