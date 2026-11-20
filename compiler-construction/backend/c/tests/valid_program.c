int add(int left, int right) {
    return left + right;
}

int main(void) {
    int total = add(1, 2);
    int i = 0;
    bool enabled = true;

    for (i = 0; i < 3; i++) {
        if (enabled) {
            total = total + i;
        }
    }

    printf(total);
    return 0;
}
