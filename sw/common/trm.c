#include <stdint.h>

#define RANGE(st, ed)       (Area) { .start = (void *)(st), .end = (void *)(ed) }
#define PMEM_SIZE (128 * 1024 * 1024)
#define PMEM_END  ((uintptr_t)&_pmem_start + PMEM_SIZE)

extern char _heap_start;
extern char _pmem_start;
extern int main(const char *args);

// Memory area for [@start, @end)
typedef struct {
  void *start, *end;
} Area;


Area heap = RANGE(&_heap_start, PMEM_END);

#ifndef MAINARGS
#define MAINARGS ""
#endif
static const char mainargs[] = MAINARGS;

#define HALT_ADDR 0x1000000C
#define UART_ADDR 0x10000000
void halt(int code) {
  // 0x0005006b is spike exit instruction
  // asm volatile("mv a0, %0; .word 0x0005006b" :: "r"(code));

    asm volatile("mv a0, %0\n\t"
                 "li t0, %1\n\t"
                 "sw a0, 0(t0)"
                 :: "r"(code), "i"(HALT_ADDR)
                 : "a0", "t0"
                 );
    //can't compile without this
    while (1);
}

void putch(char ch) {
  *(volatile char *)UART_ADDR = ch;
}

void _trm_init() {
  int ret = main(mainargs);
  halt(ret);
}
