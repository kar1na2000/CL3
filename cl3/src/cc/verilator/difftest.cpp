#include <Vtop.h>
#include <Vtop__Dpi.h>
#include <Vtop___024root.h>
#include <cassert>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <difftest.h>
#include <dlfcn.h>
#include <svdpi.h>

#ifdef __cplusplus
extern "C" {
#endif

static core_context_t dut;
static core_context_t ref;
static const Vtop *topp;

static void (*ref_difftest_memcpy)(unsigned int addr, void *buf, size_t n,
                                   bool direction) = NULL;
static void (*ref_difftest_regcpy)(void *dut, bool direction) = NULL;
static void (*ref_difftest_exec)(uint64_t n) = NULL;

static uint8_t mem[PMEM_SIZE];

static unsigned int load_img(const char *path) {
  assert(mem);
  assert(path);
  FILE *img = fopen(path, "rb");
  assert(img);

  fseek(img, 0, SEEK_END);
  unsigned int size = ftell(img);
  if (size > PMEM_SIZE) {
    fclose(img);
    return 0;
  }
  fseek(img, 0, SEEK_SET);
  int ret = fread(mem, size, 1, img);
  if (ret != 1) {
    fclose(img);
    return 0;
  }

  fclose(img);
  return size;
}

// Verilator only supports the longint DPI-C type for 64bit data.
long long mem_read(uint32_t raddr, uint32_t size) {

  if (raddr < RESET_VECTOR || (raddr - RESET_VECTOR >= PMEM_SIZE)) {
    printf("raddr is %x\n", raddr);
    return 0;
    //  assert(0);
  }

  uint32_t aligned_addr = raddr & 0xfffffffc;
  uint32_t v0 = *(uint32_t *)(mem + aligned_addr - RESET_VECTOR);
  int64_t v1 = *(int64_t *)(mem + aligned_addr - RESET_VECTOR);

  return size == 3 ? v1 : v0;
}

void mem_write(uint32_t waddr, uint32_t mask, uint32_t wdata) {
  uint8_t *addr = mem + waddr - RESET_VECTOR;
  if (waddr < RESET_VECTOR || (waddr - RESET_VECTOR >= PMEM_SIZE)) {
    printf("waddr: %#x, mask:%x, data: %#x\n", waddr, mask, wdata);
    return;
  }
  switch (mask) {
  case 0x1:
    *(volatile uint8_t *)addr = wdata;
    break;
  case 0x2:
    *(volatile uint8_t *)addr = wdata >> 8;
    break;
  case 0x4:
    *(volatile uint8_t *)addr = wdata >> 16;
    break;
  case 0x8:
    *(volatile uint8_t *)addr = wdata >> 24;
    break;
  case 0x3:
    *(volatile uint16_t *)addr = wdata;
    break;
  case 0xc:
    *(volatile uint16_t *)addr = wdata >> 16;
    break;
  case 0xf:
    *(volatile uint32_t *)addr = wdata;
    break;
  default:
    assert(0);
  }
}

void difftest_init(const Vtop *p, const char *ref_so_file,
                   const char *img_file) {
  printf("[DIFFTEST] init ...\n");
  topp = p;
  assert(topp);

  // Initialize all difftest function pointers
  void *handle;
  handle = dlopen(ref_so_file, RTLD_LAZY);
  assert(handle);
  ref_difftest_memcpy = DIFF_MEMCPY dlsym(handle, "difftest_memcpy");
  assert(ref_difftest_memcpy);
  ref_difftest_regcpy = DIFF_REGCPY dlsym(handle, "difftest_regcpy");
  assert(ref_difftest_regcpy);
  ref_difftest_exec = DIFF_EXEC dlsym(handle, "difftest_exec");
  void (*ref_difftest_init)(int) = DIFF_INIT dlsym(handle, "difftest_init");
  assert(ref_difftest_init);
  ref_difftest_init(80); // Don't care the port

  // Load image file and copy to REF
  size_t size = load_img(img_file);
  ref_difftest_memcpy(RESET_VECTOR, mem, size, DIFFTEST_TO_REF);

  // Initialize DUT and local REF state
  dut.pc = RESET_VECTOR;
  ref.pc = RESET_VECTOR;
  ref.csr[2] = TRAP_VECTOR;

  // Initialize remote REF state
  ref_difftest_regcpy((void *)&ref, DIFFTEST_TO_REF);
  printf("[DIFFTEST] finish initialization\n");
  printf("[DIFFTEST] image name: %s, image size : %ld\n", img_file, size);
}

void update_dut_state() { GET_ALL_GPR }

// the parameter type is 'svOpenArrayHandle' in Verilator
int difftest_step(int n, svOpenArrayHandle info) {

  difftest_info_t *diff_info_ptr = (difftest_info_t *)svGetArrayPtr(info);

  int i;
  uint32_t npc;
  for (i = 0; i < n; i++) {
    if (diff_info_ptr[i].commit) {
      npc = diff_info_ptr[i].npc;
      ref_difftest_exec(1);
      ref_difftest_regcpy((void *)&ref, DIFFTEST_TO_DUT);
      if (ref.pc != npc) {
        printf(COLOR_RED "[DIFFTEST] Mismatch in PC %0#x: "
               "DUT's NPC is different from REF's."
               "Maybe there is an wrong branch/jump/CSR Instruction"
               "or trap.\n" COLOR_END,
               diff_info_ptr[i].pc);

        return 1;
      }

      // GPR Check
      uint32_t wdata = diff_info_ptr[i].wdata;
      uint16_t rdIdx = diff_info_ptr[i].rdIdx;
      if (diff_info_ptr[i].wen && wdata != ref.gpr[rdIdx]) {
        printf(COLOR_RED "[DIFFTEST] Mismatch in PC %0#x: "
               "DUT's GPR[%d] is different from REF's. REF's is %0#x "
               "but DUT's is %0#x.\n" COLOR_END,
               diff_info_ptr[i].pc, rdIdx, ref.gpr[rdIdx], wdata);

        return 1;
      }
    }
  }

  // Double Check
  update_dut_state();
  int gpr_mask = 0;
  for (int i = 0; i < GPR_NUM; i++) {
    if (dut.gpr[i] != ref.gpr[i])
      gpr_mask |= (1U << i);
  }

  if (gpr_mask) {
    printf( COLOR_RED "[DIFFTEST] Mismatch in double check: "
           "Maybe something changed the dut state unexpectedly.\n" COLOR_END);
    
    return 1;
  }

  return 0;
}

#ifdef __cplusplus
}
#endif
