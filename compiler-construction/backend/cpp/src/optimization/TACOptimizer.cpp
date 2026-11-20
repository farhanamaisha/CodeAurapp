#include "TACOptimizer.hpp"

#include <sstream>

namespace compiler_cpp {

namespace {

bool isInteger(const std::string& value) {
    if (value.empty()) {
        return false;
    }
    std::size_t start = (value[0] == '-' || value[0] == '+') ? 1 : 0;
    if (start == value.size()) {
        return false;
    }
    for (std::size_t i = start; i < value.size(); ++i) {
        if (value[i] < '0' || value[i] > '9') {
            return false;
        }
    }
    return true;
}

long long toInteger(const std::string& value) {
    try {
        return std::stoll(value);
    } catch (...) {
        return 0;
    }
}

std::string foldConstants(const std::string& line) {
    const std::string assignment = " = ";
    const std::size_t assignmentPosition = line.find(assignment);
    if (assignmentPosition == std::string::npos) {
        return line;
    }

    std::istringstream expression(line.substr(assignmentPosition + assignment.size()));
    std::string left;
    std::string op;
    std::string right;
    if (!(expression >> left >> op >> right)) {
        return line;
    }

    if (!isInteger(left) || !isInteger(right)) {
        if (op == "+" && right == "0") {
            return line.substr(0, assignmentPosition) + " = " + left;
        }
        if (op == "-" && right == "0") {
            return line.substr(0, assignmentPosition) + " = " + left;
        }
        if (op == "*" && (left == "1" || right == "1")) {
            const std::string operand = left == "1" ? right : left;
            return line.substr(0, assignmentPosition) + " = " + operand;
        }
        if (op == "/" && right == "1") {
            return line.substr(0, assignmentPosition) + " = " + left;
        }
        return line;
    }

    const long long lhs = toInteger(left);
    const long long rhs = toInteger(right);
    long long result = 0;
    bool supported = true;

    if (op == "+") result = lhs + rhs;
    else if (op == "-") result = lhs - rhs;
    else if (op == "*") result = lhs * rhs;
    else if (op == "/") {
        if (rhs == 0) supported = false;
        else result = lhs / rhs;
    }
    else if (op == "%") {
        if (rhs == 0) supported = false;
        else result = lhs % rhs;
    }
    else supported = false;

    if (!supported) {
        return line;
    }
    return line.substr(0, assignmentPosition) + " = " + std::to_string(result);
}

}  // namespace

std::vector<std::string> TACOptimizer::optimize(const std::vector<std::string>& tac) {
    std::vector<std::string> optimized;
    optimized.reserve(tac.size());

    for (std::size_t i = 0; i < tac.size(); ++i) {
        std::string line = foldConstants(tac[i]);
        if (i + 1 < tac.size()) {
            const std::string next = tac[i + 1];
            if (next.rfind("goto ", 0) == 0) {
                // Empty optimization pass intentionally preserves control-flow structure.
            }
        }
        optimized.push_back(line);
    }

    return optimized;
}

}  // namespace compiler_cpp
