

// Use the 2-state 'bit' type instead of the 4-state 'logic' type.
// This ensures a C-compatible memory layout for DPI-C communication.
// Consequently, Verilator will set the 'VLVF_DPI_CLAY' flag on arrays
// of this struct, enabling the use of the efficient svGetArrayPtr() function.

typedef struct packed {
  bit [31:0] pc;
  bit [31:0] npc;
  bit [31:0] inst;
  bit [15:0] rdIdx;
  bit [15:0] wen;
  bit [31:0] wdata;
  bit [15:0] commit;
  bit [15:0] skip;   
} difftest_info_t;