package cl3

import chisel3._
import chisel3.util._

class MemHelper extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val req   = Flipped(Decoupled(Input(new SimpleMemReq(32))))
    val resp  = Decoupled(Output(new SimpleMemResp(64)))
    val reset = Input(Bool())
    val clock = Input(Clock())
  })
  setInline(
    "mem_helper.sv",
    """
module MemHelper(
	input  logic req_valid,
	output logic req_ready,
	input  logic [31:0] req_bits_addr,
	input  logic req_bits_wen,
	input  logic [31:0] req_bits_wdata,
	input  logic [3:0]  req_bits_mask,
    input  logic req_bits_cacheable,
    input  logic [2:0]  req_bits_size, 
	output logic resp_valid, 
	input  logic resp_ready,
	output logic [3:0] resp_bits_err,
	output logic [63:0] resp_bits_rdata,
	input  logic reset,
	input  logic clock
);
	import "DPI-C" function longint mem_read(input int unsigned raddr, input int unsigned size);
	import "DPI-C" function void mem_write(input int unsigned waddr, input int unsigned mask, input int unsigned wdata);

	logic [63:0] rdata_q;
	assign resp_bits_rdata = rdata_q;

	typedef enum logic [1:0] {
		IDLE = 2'b00,
		ACTIVE = 2'b01
	} state_t;

	state_t cs, ns;

	logic [63:0] mtime_q;
	logic [31:0] mtimeh_lock_q;

	always_ff @(posedge clock) begin
	  if(reset)
	    mtime_q <= 64'b0;
	  else
		mtime_q <= mtime_q + 64'b1;
	end

	always_ff @(posedge clock) begin
		if(reset)
			cs <= IDLE;
		else
			cs <= ns;
	end

	logic sel_uart;
	logic sel_finish;
	byte uart_char;

	assign sel_uart   = (req_bits_addr == 32'h10000000);
	assign sel_finish = (req_bits_addr == 32'h1000000C);
	assign sel_mtimel = (req_bits_addr == 32'h10000010);
	assign sel_mtimeh = (req_bits_addr == 32'h10000014);
	assign uart_char  = req_bits_wdata[7:0];

	always_ff @(posedge clock) begin
		if(reset) begin
  			rdata_q <= 64'b0;
		end
		else begin
			if(req_valid & req_ready) begin
  				if(!req_bits_wen && !(sel_mtimel || sel_mtimeh))
  					rdata_q <= mem_read(req_bits_addr, req_bits_size);
				else if(!req_bits_wen && sel_mtimel) begin
				    rdata_q <= mtime_q[31:0];
					mtimeh_lock_q <= mtime_q[63: 32];
				end
				else if(!req_bits_wen && sel_mtimeh)
					rdata_q <= mtimeh_lock_q;
				else if(req_bits_wen & sel_uart)
            		$write("%s", uart_char);
          		else if(req_bits_wen & sel_finish)
            		$finish();
          	else
			 	mem_write(req_bits_addr, req_bits_mask, req_bits_wdata);
			end
		end
	end

	always_comb begin
		ns = cs;
		case (cs)
			IDLE: begin
				if(req_valid)
					ns = ACTIVE;
			end
			ACTIVE: begin
				if(!req_valid & resp_ready) begin
					ns = IDLE;
				end
			end
		endcase
	end

	assign resp_bits_err = 4'b0;
	always_ff @(posedge clock) begin
		if(reset) begin
			req_ready <= 1'b1;
			resp_valid <= 1'b0;
		end
		else begin
			case (ns)
				IDLE: begin
					req_ready  <= 1'b1;
					resp_valid <= 1'b0;
				end
				ACTIVE: begin
					req_ready  <= 1'b1;
					resp_valid <= 1'b1;
				end
				default: begin
  				req_ready  <= 1'b0;
					resp_valid <= 1'b0;
				end
            endcase
        end
    end
endmodule
  """.stripMargin
  )

}
