import difftest_pkg::*;
    module Difftest #(
      parameter int NR_COMMIT_PORTS = 2
    )(
      input logic clock,
      input logic reset,
      difftest_pkg::difftest_info_t [0 : NR_COMMIT_PORTS-1] diff_info
    );
    
    import "DPI-C" function int difftest_step(input int n, input difftest_pkg::difftest_info_t info[]);

    int ret;
    logic commit;

    difftest_pkg::difftest_info_t diff_info_q [0 : NR_COMMIT_PORTS-1] ;

    always_ff @(posedge clock) begin
      if (reset) begin
        foreach (diff_info_q[i]) begin
          diff_info_q[i] <= '{default: '0};
        end
      end else begin
        foreach (diff_info_q[i]) begin
          diff_info_q[i] <= diff_info[i];
        end
      end
    end

    always_comb begin

      commit = 1'b0;
      for(int i = 0; i < NR_COMMIT_PORTS; i++) begin
        commit = commit | diff_info_q[i].commit;
      end
    end

    always_ff @(posedge clock) begin

      if(commit) begin
        ret = 1'b0;
        ret = difftest_step(NR_COMMIT_PORTS, diff_info_q);
        if(ret) begin
          $fatal("HIT BAD TRAP!");
        end
      end
    end

  endmodule

//   module Difftest(
//   input  logic          clock,
//   input  logic          reset,
//   difftest_info_t [1:0] diff
// );

//   import "DPI-C" function void difftest_step(input int unsigned pc, input int unsigned npc);
//   import "DPI-C" function void difftest_skip_dut(input int nr_ref, input int nr_dut);

//   int ret;
//   difftest_info_t [1:0] diff_q;

//   always_ff @(posedge clock) begin
//     if (reset) begin
//       diff_q[0] <= '0;
//       diff_q[1] <= '0;
//     end else begin
//       diff_q[0] <= diff[0];
//       diff_q[1] <= diff[1];
//     end
//   end

//   function automatic int calc_npc(input int pc, input int inst);
//     if ((inst & 3) == 3) calc_npc = pc + 4; else calc_npc = pc + 2;
//   endfunction

//   always_ff @(posedge clock) begin
//     for (int i = 0; i < 2; i++) begin
//       if (diff_q[i].commit && !diff_q[i].skip) begin
//         int npc = calc_npc(diff_q[i].pc, diff_q[i].inst);
//         difftest_step(diff_q[i].pc, npc);
//       end else if (diff_q[i].commit && diff_q[i].skip) begin
//         difftest_skip_dut(0, 1);
//       end
//     end
//   end

// endmodule