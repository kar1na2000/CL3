module top #(
    parameter logic [31:0] BOOT_ADDR = 'h80000000,
    parameter CLK_HI = 5ns,
    parameter CLK_LO = 5ns,
    parameter RESET_WAIT_CYCLES = 20,
    parameter TO_CNT = 5000000000
);

// Add this empty function to make verilator add some header files (DPI).
export "DPI-C" function make_verilator_happy;

function void make_verilator_happy();
endfunction

logic       clk     = 'b1;
logic       rst_n   = 'b0;

initial begin: clock_gen 
    forever begin
        #CLK_HI clk = 1'b0;
        #CLK_LO clk = 1'b1;
    end
end

initial begin: reset_gen
    rst_n = 1'b0;
    repeat (RESET_WAIT_CYCLES) begin
        @(posedge clk);
    end
    rst_n = 1'b1;
    $display("[TESTBENCH] reset deasserted: %d", $time);
    $display("[TESTBENCH] note: disable waveform generation and difftest for better performance");

end: reset_gen

// timeout
logic [31:0] cnt;
always_ff @( posedge clk or negedge rst_n ) begin : timeout
    if(~rst_n) begin
        cnt <= 'd0;
    end else begin
        cnt <= cnt + 'd1;
        if(cnt == TO_CNT) begin
            $fatal("[TESTBENCH] time out!");
        end
    end
end: timeout

// TODO: use sv testbench

initial begin: dump_wave
    if($test$plusargs("vcd")) begin
        $dumpfile("wave/top.vcd");
        $dumpvars(0,top);
    end 
    if($test$plusargs("fst")) begin
        $dumpfile("wave/top.fst");
        $dumpvars(0,top);
    end
`ifdef VCS
    if($test$plusargs("fsdb")) begin
        $fsdbDumpfile("wave/top.fsdb");
        $fsdbDumpvars(0,top);
    end
`endif
end: dump_wave

  CL3Top u_CL3Top (
    .clock                    (clk),
    .reset                    (~rst_n),

    .io_extIrq                (1'b0),
    .io_timerIrq              (1'b0),
    .io_master_aw_ready       (1'b0),
    .io_master_w_ready        (1'b0),
    .io_master_b_valid        (1'b0),
    .io_master_b_bits_bresp   (2'b0),
    .io_master_b_bits_bid     (4'b0),
    .io_master_ar_ready       (1'b0),
    .io_master_r_valid        (1'b0),
    .io_master_r_bits_rresp   (2'b0),
    .io_master_r_bits_rdata   (32'b0),
    .io_master_r_bits_rlast   (1'b0),
    .io_master_r_bits_rid     (4'b0),
    
    .io_master_aw_valid       (),
    .io_master_aw_bits_awaddr (),
    .io_master_aw_bits_awid   (),
    .io_master_aw_bits_awlen  (),
    .io_master_aw_bits_awsize (),
    .io_master_aw_bits_awburst(),
    .io_master_aw_bits_awlock (),
    .io_master_aw_bits_awcache(),
    .io_master_aw_bits_awprot (),
    .io_master_w_valid        (),
    .io_master_w_bits_wdata   (),
    .io_master_w_bits_wstrb   (),
    .io_master_w_bits_wlast   (),
    .io_master_b_ready        (),
    .io_master_ar_valid       (),
    .io_master_ar_bits_araddr (),
    .io_master_ar_bits_arid   (),
    .io_master_ar_bits_arlen  (),
    .io_master_ar_bits_arsize (),
    .io_master_ar_bits_arburst(),
    .io_master_ar_bits_arlock (),
    .io_master_ar_bits_arcache(),
    .io_master_ar_bits_arprot (),
    .io_master_r_ready        ()
  );
endmodule