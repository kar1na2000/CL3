module difftest_wrapper (
  input logic clock,
  input logic reset,

  input logic [31:0] diff_0_pc,
  input logic [31:0] diff_0_inst,
  input logic        diff_0_commit,
  input logic        diff_0_skip,

  input logic [31:0] diff_1_pc,
  input logic [31:0] diff_1_inst,
  input logic        diff_1_commit,
  input logic        diff_1_skip
);

  difftest_info_t[1:0] diff_packed;

  assign diff_packed[0].pc     = diff_0_pc;
  assign diff_packed[0].inst   = diff_0_inst;
  assign diff_packed[0].commit = diff_0_commit;
  assign diff_packed[0].skip   = diff_0_skip;
  
  assign diff_packed[1].pc     = diff_1_pc;
  assign diff_packed[1].inst   = diff_1_inst;
  assign diff_packed[1].commit = diff_1_commit;
  assign diff_packed[1].skip   = diff_1_skip;

  Difftest difftest_inst (
    .clock(clock),
    .reset(reset),
    .diff(diff_packed)
  );

endmodule