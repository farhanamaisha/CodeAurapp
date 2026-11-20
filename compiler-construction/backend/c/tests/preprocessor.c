#include "config.h"
#include <stdio.h>

#define ENABLE_FEATURE 1

int main(void) {
    int value = START_VALUE;
#if ENABLE_FEATURE
    value += INCREMENT_VALUE;
#else
    value += 99;
#endif
#ifndef UNDEFINED_FEATURE
    value++;
#endif
    printf(value);
    return 0;
}
