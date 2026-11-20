int sum(int values[], int length) {
    int total = 0;
    int i = 0;

    for (i = 0; i < length; i++) {
        total += values[i];
    }

    return total;
}

int main(void) {
    int values[3];
    int first = 1;
    int *pointer;

    values[0] = first;
    values[1] = 2;
    values[2] = 3;
    pointer = &values[0];
    *pointer = 4;

    printf(values[0]);
    return sum(values, 3);
}
