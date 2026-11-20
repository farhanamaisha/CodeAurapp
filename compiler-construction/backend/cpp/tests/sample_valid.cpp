#include <iostream>
#include <string>

using namespace std;

int main() {
    string name;
    int age;

    // Prompt and read text (single word)
    cout << "Enter your first name: ";
    cin >> name;

    // Prompt and read a number
    cout << "Enter your age: ";
    cin >> age;

    // Display the output
    cout << "Hello, " << name << "! You are " << age << " years old." << endl;

    return 0;
}