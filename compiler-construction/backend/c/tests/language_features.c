int main(void) {
    char first = 'A', second = 'B';
    int value = 1, other = 2;

    value += other;
    value *= +first;
    ++value;
    value--;

    printf(value);
    return value;
}
