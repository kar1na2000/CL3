
    module Difftest(
      input logic clock,
      input logic reset,
      difftest_info_t[1:0] diff
    );
    
    import "DPI-C" function int difftest_step(input int pc, input int inst, input int c_inst, input int is_c_inst);
    import "DPI-C" function void difftest_skip(input int pc, input int is_c_inst);

    int ret;
    difftest_info_t[1:0] diff_q;

    always_ff @(posedge clock) begin
      if(reset) begin
        diff_q[0] <= 0;
        diff_q[1] <= 0;
      end
      else begin
        diff_q[0] <= diff[0];
        diff_q[1] <= diff[1];
      end
    end

    always_ff @(posedge clock) begin
      for(int i = 0; i < 2; i++) begin
        if(diff_q[i].commit && !diff_q[i].skip) begin
          ret = difftest_step(diff_q[i].pc, diff_q[i].inst, 0, 0);
          if(ret) begin
            $fatal("HIT BAD TRAP!");
          end
        end 
        else if(diff_q[i].commit && diff_q[i].skip) begin
          difftest_skip(diff_q[i].pc, 0);
        end
      end
    end
  endmodule