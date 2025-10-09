typedef struct packed {
  logic [31:0] pc;
  logic [31:0] inst;
  logic commit;
  logic skip;
} difftest_info_t;