#include "CodeGenerator.hpp"

#include <sstream>

namespace compiler_cpp {

namespace {

std::string opcodeFor(const std::string& op) {
    if (op == "+") return "ADD";
    if (op == "-") return "SUB";
    if (op == "*") return "MUL";
    if (op == "/") return "DIV";
    if (op == "%") return "MOD";
    if (op == "==") return "EQ";
    if (op == "!=") return "NE";
    if (op == "<") return "LT";
    if (op == "<=") return "LE";
    if (op == ">") return "GT";
    if (op == ">=") return "GE";
    if (op == "&&") return "AND";
    if (op == "||") return "OR";
    return op;
}

}  // namespace

std::vector<std::string> CodeGenerator::generate(const std::vector<std::string>& tac) {
    std::vector<std::string> code;
    code.push_back("; Generated pseudo-assembly");
    code.push_back(".text");
    code.push_back("main:");

    for (const std::string& line : tac) {
        if (!line.empty() && line.back() == ':') {
            code.push_back(line);
            continue;
        }
        if (line.rfind("ifFalse ", 0) == 0) {
            std::istringstream input(line);
            std::string keyword;
            std::string condition;
            std::string gotoKeyword;
            std::string label;
            input >> keyword >> condition >> gotoKeyword >> label;
            code.push_back("  CMP " + condition + ", 0");
            code.push_back("  JE " + label);
            continue;
        }
        if (line.rfind("goto ", 0) == 0) {
            code.push_back("  JMP " + line.substr(5));
            continue;
        }
        if (line.rfind("print ", 0) == 0) {
            const std::string value = line.substr(6);
            code.push_back(value == "endl" ? "  PRINTLN" : "  PRINT " + value);
            continue;
        }
        if (line.rfind("read ", 0) == 0) {
            code.push_back("  READ " + line.substr(5));
            continue;
        }
        if (line.rfind("readline ", 0) == 0) {
            code.push_back("  READLINE " + line.substr(9));
            continue;
        }
        if (line.rfind("return", 0) == 0) {
            if (line == "return") {
                code.push_back("  RET");
            } else {
                code.push_back("  RET " + line.substr(7));
            }
            continue;
        }

        const std::string assignment = " = ";
        const std::size_t position = line.find(assignment);
        if (position != std::string::npos) {
            const std::string target = line.substr(0, position);
            const std::string expression = line.substr(position + assignment.size());
            std::istringstream input(expression);
            std::string left;
            std::string op;
            std::string right;
            if (input >> left >> op >> right) {
                code.push_back("  " + opcodeFor(op) + " " + target + ", " + left + ", " + right);
            } else {
                code.push_back("  MOV " + target + ", " + expression);
            }
            continue;
        }
    }

    code.push_back("  HALT");
    return code;
}

}  // namespace compiler_cpp
