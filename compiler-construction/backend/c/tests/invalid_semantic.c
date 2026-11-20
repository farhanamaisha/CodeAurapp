int bad(void) {
    int value = true;
    unknown = value + 1;
    return 0;
}

int main(void) {
    return bad(1);
}
