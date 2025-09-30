#include <stddef.h>
#include "klib.h"
size_t strlen(const char *s) {
    size_t str_len = 0;
    while(*s != '\0') {
      s++;
      str_len++;
    }
    return str_len;
  }
  
  char *strcpy(char *dst, const char *src) {
    char *p_dst = dst;
    while((*p_dst++ = *src++) !=0);
    return dst;
  }
  
  char *strncpy(char *dst, const char *src, size_t n) {
    size_t i;
    for(i=0; (i < n)&&(src[i] != 0); i++) {
      dst[i] = src[i];
    }
    for(; i < n; i++)
      dst[i] = 0;
    return dst;
  }
  
  char *strcat(char *dst, const char *src) {
    size_t dst_len = strlen(dst);
    size_t i;
    for(i=0;src[i] != 0;i++) {
      dst[dst_len + i] = src[i];
    }
    dst[dst_len + i] = 0;
    return dst;
  }
  
  int strcmp(const char *s1, const char *s2) {
    size_t i;
    size_t rslt;
    for(i=0;(s1[i] != 0) && (s2[i] != 0);i++) {
      if(s1[i] != s2[i]) break;
    }
    rslt = (unsigned char)s1[i] - (unsigned char)s2[i];
    return rslt;
  }
  
  int strncmp(const char *s1, const char *s2, size_t n) {
    size_t i;
    size_t rslt;
    for(i=0; (i<n) && (s1[i] != 0) && (s2[i] != 0);i++) {
      if(s1[i] != s2[i]) break;
    }
    rslt = (i == n) ? 0 : ((unsigned char)s1[i] - (unsigned char)s2[i]);
    return rslt;
  }
  
  void *memset(void *s, int c, size_t n) {
    char *p = (char *)s;
    while(n--) {
      *p++ = (char)c;
    }
    return s;
  }
  
  void *memmove(void *dst, const void *src, size_t n) {
    char *s = (char *)src;
    char *d = (char *)dst;
    if(s >= d) {
      while(n) {
      *d++ = *s++;
      n--;
      }
    } else {
      while(n) {
        --n;
        d[n] = s[n];
      }
    }
  
    return dst;
  }
  
  void *memcpy(void *out, const void *in, size_t n) {
    const char *src = in;
    char *dst = out;
    while(n) {
      *dst++ = *src++;
      n--;
    }
    return out;
  }
  
  int memcmp(const void *s1, const void *s2, size_t n) {
    int r = 0;
    const unsigned char *r1 = s1;
    const unsigned char *r2 = s2;
    while(n-- && ((r= ((int)(*r1++)) - *r2++) == 0));
    return r;
  }