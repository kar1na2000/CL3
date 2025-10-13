module difftest_wrapper (
  input logic clock,
  input logic reset,

  // Inputs for the first core/pipeline to be compared
  input logic [31:0] diff_info_0_pc,
  input logic [31:0] diff_info_0_npc,
  input logic [31:0] diff_info_0_inst,
  input logic [4:0]  diff_info_0_rdIdx,
  input logic        diff_info_0_wen,
  input logic [31:0] diff_info_0_wdata,
  input logic        diff_info_0_commit,
  input logic        diff_info_0_skip,

  // Inputs for the second core/pipeline to be compared
  input logic [31:0] diff_info_1_pc,
  input logic [31:0] diff_info_1_npc,
  input logic [31:0] diff_info_1_inst,
  input logic [4:0]  diff_info_1_rdIdx,
  input logic        diff_info_1_wen,
  input logic [31:0] diff_info_1_wdata,
  input logic        diff_info_1_commit,
  input logic        diff_info_1_skip
);

  difftest_info_t[1:0] diff_packed;

  // --- Assignments for the first set of inputs (index 0) ---
  assign diff_packed[0].pc     = diff_info_0_pc;
  assign diff_packed[0].npc    = diff_info_0_npc;
  assign diff_packed[0].inst   = diff_info_0_inst;
  assign diff_packed[0].rdIdx  = diff_info_0_rdIdx;
  assign diff_packed[0].wen    = diff_info_0_wen;
  assign diff_packed[0].wdata  = diff_info_0_wdata;
  assign diff_packed[0].commit = diff_info_0_commit;
  assign diff_packed[0].skip   = diff_info_0_skip;
  
  // --- Assignments for the second set of inputs (index 1) ---
  assign diff_packed[1].pc     = diff_info_1_pc;
  assign diff_packed[1].npc    = diff_info_1_npc;
  assign diff_packed[1].inst   = diff_info_1_inst;
  assign diff_packed[1].rdIdx  = diff_info_1_rdIdx;
  assign diff_packed[1].wen    = diff_info_1_wen;
  assign diff_packed[1].wdata  = diff_info_1_wdata;
  assign diff_packed[1].commit = diff_info_1_commit;
  assign diff_packed[1].skip   = diff_info_1_skip;

  // Difftest module instantiation
  Difftest difftest_inst (
    .clock(clock),
    .reset(reset),
    .diff_info(diff_packed)
  );

endmodule