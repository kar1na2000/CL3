
    module Difftest #(
      parameter int NR_COMMIT_PORTS = 2
    )(
      input logic clock,
      input logic reset,
      difftest_info_t[1:0] diff_info
    );
    
    import "DPI-C" function int difftest_step(input int n, input difftest_info_t info[]);

    int ret;
    logic commit;

    difftest_info_t[NR_COMMIT_PORTS - 1:0] diff_info_q;

    always_ff @(posedge clock) begin
      if(reset) begin
        diff_info_q <= 0;
      end
      else 
        diff_info_q <= diff_info;
    end

    always_comb begin

      commit = 1'b0;
      for(int i = 0; i < NR_COMMIT_PORTS; i++) begin
        commit = commit | diff_info_q[i].commit;
      end
    end

    always_ff @(posedge clock) begin

      if(commit) begin
        ret = difftest_step(NR_COMMIT_PORTS, diff_info_q);
        if(ret) begin
          $fatal("HIT BAD TRAP!");
        end
      end
    end

  endmodule