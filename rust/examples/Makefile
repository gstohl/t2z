# Makefile for PCZT C examples

CC = gcc
CFLAGS = -Wall -Wextra -I../include
LDFLAGS = -L../target/release -lt2z

# Platform-specific settings
UNAME_S := $(shell uname -s)
ifeq ($(UNAME_S),Darwin)
    # macOS
    LIB_PATH_VAR = DYLD_LIBRARY_PATH
    LIB_EXT = dylib
else ifeq ($(UNAME_S),Linux)
    # Linux
    LIB_PATH_VAR = LD_LIBRARY_PATH
    LIB_EXT = so
else
    # Windows (MSYS/MinGW)
    LIB_EXT = dll
endif

.PHONY: all clean run

all: example

example: example.c ../include/t2z.h
	@echo "Building example..."
	$(CC) $(CFLAGS) -o example example.c $(LDFLAGS)
	@echo "Build complete!"

run: example
	@echo "Running example..."
	$(LIB_PATH_VAR)=../target/release ./example

clean:
	rm -f example
	@echo "Cleaned build artifacts"

help:
	@echo "PCZT C Examples Makefile"
	@echo ""
	@echo "Targets:"
	@echo "  all     - Build the example program (default)"
	@echo "  run     - Build and run the example"
	@echo "  clean   - Remove build artifacts"
	@echo "  help    - Show this help message"
	@echo ""
	@echo "Note: Make sure to build the Rust library first:"
	@echo "  cd .. && cargo build --release"
