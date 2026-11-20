#include <iostream>
#include <string>

int main() {
    std::string firstName;
    std::string fullName;
    int age;

    std::cin >> firstName;
    std::cin >> age;
    std::getline(std::cin, fullName);

    return 0;
}
