CC ?= cc
CFLAGS ?= -std=c99 -Wall -Wextra -Wpedantic -O2

SOURCES := $(wildcard src/*.c)
TARGET := c-compiler

all: $(TARGET)

$(TARGET): $(SOURCES) src/compiler.h
	$(CC) $(CFLAGS) $(SOURCES) -o $@

test: $(TARGET)
	tests/run-tests.sh ./$(TARGET)

clean:
	rm -f $(TARGET)

.PHONY: all clean test
