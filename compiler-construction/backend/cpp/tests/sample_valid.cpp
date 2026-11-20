#include <iostream>
#include <string>

int main() {
    std::string fullName;

    std::cout << "Enter your full name: ";
    std::getline(std::cin, fullName); // Reads the entire line until Enter is pressed

    std::cout << "Welcome, " << fullName << "!\n";
    return 0;
}