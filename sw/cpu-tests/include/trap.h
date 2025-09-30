#ifndef __TRAP_H__
#define __TRAP_H__
#include <stdbool.h>
#include <stdint.h>
#include "klib.h"
#define LENGTH(arr)         (sizeof(arr) / sizeof((arr)[0]))
void halt(int code);
__attribute__((noinline))
void check(bool cond) {
  if (!cond) halt(1);
}

#endif
