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
  int64_t  v1 = *(int64_t  *)(mem + aligned_addr - RESET_VECTOR);

  return size == 3 ? v1 : v0;
}

void mem_write(uint32_t waddr, uint32_t mask, uint32_t wdata) {
  uint8_t *addr = mem + waddr - RESET_VECTOR;
  // printf("waddr: %#x, mask:%x, data: %#x\n", waddr, mask, wdata);
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

void update_dut_state(uint32_t pc) {
  dut.pc = pc;
  GET_ALL_GPR
  dut.csr[0] = MEPC;
  dut.csr[1] = MCAUSE;
  dut.csr[2] = MTVEC;
}

void difftest_info(bool pc_flag, int gpr_mask, int csr_mask) {
  printf("[DIFFTEST] DUT's PC is %0#8x. \n", dut.pc);
  if (pc_flag)
    printf(
        "[DIFFTEST]" COLOR_RED
        " DUT's PC is different from REF's PC, REF's PC is %0#8x.\n" COLOR_END,
        ref.pc);

  if (gpr_mask) {
    for (int i = 0; i < GPR_NUM; i++) {
      if ((gpr_mask >> i) & 1)
        printf("[DIFFTEST]" COLOR_RED
               " DUT's GPR[%d] is different from REF's. The value is %0#8x, "
               "which should be %0#8x. \n" COLOR_END,
               i, dut.gpr[i], ref.gpr[i]);
    }
  }

  // TODO: add CSRs and use map
  if (csr_mask) {
    for (int i = 0; i < 3; i++) {
      if ((csr_mask >> i) & 1)
        printf("[DIFFTEST]" COLOR_RED
               " DUT's CSR[%d] is different from REF's. The value is %0#8x, "
               "which should be %0#8x. \n" COLOR_END,
               i, dut.csr[i], ref.csr[i]);
    }
  }
}

void difftest_skip(int pc, int is_c_instr) {
  int offset = 4;
  if (is_c_instr) {
    offset = 2;
  }
  // The PC of the DUT is the address of the most recently executed instruction,
  // while the PC of the REF is the address of the next instruction to be
  // executed.
  update_dut_state(pc + offset);
  ref_difftest_regcpy((void *)&dut, DIFFTEST_TO_REF);
}

int difftest_step(int pc, int instr, int c_instr, int is_c_instr) {
  update_dut_state(pc);

  // Check PC
  bool pc_flag = false;
  ref_difftest_regcpy((void *)&ref, DIFFTEST_TO_DUT);
  if (ref.pc != dut.pc)
    pc_flag = true;

  // Check GPRs
  int gpr_mask = 0;
  ref_difftest_exec(1);
  ref_difftest_regcpy((void *)&ref, DIFFTEST_TO_DUT);
  for (int i = 0; i < GPR_NUM; i++) {
    if (dut.gpr[i] != ref.gpr[i])
      gpr_mask |= (1U << i);
  }

  int csr_mask = 0;
  // TODO: add CSRs
  // for (int i = 0; i < 3; i++) {
  //   if (dut.csr[i] != ref.csr[i]) {
  //     csr_mask |= (1U << i);
  //   }
  // }

  if (pc_flag || gpr_mask || csr_mask) {
    difftest_info(pc_flag, gpr_mask, csr_mask);
    return 1;
  }

  return 0;
}

#ifdef __cplusplus
}
#endif
