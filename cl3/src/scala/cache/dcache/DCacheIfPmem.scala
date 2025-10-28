package cl3

import chisel3._
import chisel3.util._

class dcache_if_pmem(p: DCacheParams) extends Module {
    val io = IO(new DCacheIfPMemIO(p))
    val (req, resp, accept, pmem_req, pmem_resp) = (io.cpu.req, io.cpu.resp, io.cpu.accept, io.pmem.req, io.pmem.resp)
    
    val res_accept_w = Wire(Bool())
    val request_complete_w = Wire(Bool())
    val req_pop_w = request_complete_w

    val u_req  = Module(new dcache_if_pmem_fifo(WIDTH = (p.addrW + p.dataW + p.wstrbW + 1 + 1), DEPTH = 2, ADDR_W = 1))
    val req_accept_w = u_req.io.accept_o
    val req_valid_w  = u_req.io.valid_o
    val req_w        = u_req.io.out_data

    val u_resp = Module(new dcache_if_pmem_fifo(WIDTH = 11, DEPTH = 2, ADDR_W = 1))
    res_accept_w   := u_resp.io.accept_o
    resp.respTag := u_resp.io.out_data
    u_resp.io.pop_i := resp.ack
    u_resp.io.in_data := req.reqTag

// Cache requests are dropped
// NOTE: Should not actually end up here if configured correctly.
    val drop_req_w = req.invalidate || req.writeback || req.flush
    // val request_w  = drop_req_w || req.rd || (req.rd && (req.wr =/= 0.U))
    val request_w  = drop_req_w || req.rd


// Push on transaction and other FIFO not full
    val req_push_w = request_w && res_accept_w
    u_req.io.in_data := Cat(drop_req_w, !(req.wr.orR), req.wr, req.dataWr, req.addr)
    u_req.io.push_i  := req_push_w
    u_req.io.pop_i   := req_pop_w

    accept := req_accept_w & res_accept_w
    val res_push_w = request_w && req_accept_w
    u_resp.io.push_i := res_push_w

//-------------------------------------------------------------
// Request
//-------------------------------------------------------------
    val request_pending_q = RegInit(false.B)
    val request_in_progress_w  = request_pending_q & !resp.ack
    val req_is_read_w          = Mux((req_valid_w & !request_in_progress_w), req_w(68), 0.U)
    val req_is_write_w         = Mux((req_valid_w & !request_in_progress_w), !req_w(68), 0.U)
    val req_is_drop_w          = Mux((req_valid_w & !request_in_progress_w), req_w(69), 0.U)

    pmem_req.wr   := Mux(req_is_write_w.asBool, req_w(67, 64), 0.U)
    pmem_req.rd   := req_is_read_w
    pmem_req.len  := 0.U
    pmem_req.addr := Cat(req_w(p.addrW - 1, 2), 0.U(2.W))
    pmem_req.writeData := req_w(p.addrW + p.dataW - 1, p.addrW)

    request_complete_w :=  req_is_drop_w.asBool || ((pmem_req.rd || pmem_req.wr =/= 0.U) && pmem_resp.accept)

// Outstanding Request Tracking
    when(request_complete_w){
        request_pending_q := true.B
    } .elsewhen(resp.ack){
        request_pending_q := false.B
    }

//-------------------------------------------------------------
// Response
//-------------------------------------------------------------
    val dropped_q = RegInit(false.B)

    when(req_is_drop_w.asBool){
        dropped_q := true.B
    } .otherwise{
        dropped_q := false.B
    }

    resp.ack := dropped_q || pmem_resp.ack
    resp.dataRd   := pmem_resp.readData
    resp.error    := pmem_resp.error

}



class dcache_if_pmem_fifo(WIDTH: Int = 8, DEPTH: Int = 4, ADDR_W: Int = 2) extends Module {
    val io = IO(new DCacheAxiFifoIO(WIDTH, DEPTH, ADDR_W))

    val COUNT_W = ADDR_W + 1

    val ram_q = RegInit(VecInit(Seq.fill(DEPTH)(0.U(WIDTH.W))))
    val rd_ptr_q = RegInit(0.U(ADDR_W.W))
    val wr_ptr_q = RegInit(0.U(ADDR_W.W))
    val count_q = RegInit(0.U(COUNT_W.W))

    when(io.push_i && io.accept_o) {
        ram_q(wr_ptr_q) := io.in_data
        wr_ptr_q := wr_ptr_q + 1.U
    }
    when(io.pop_i && io.valid_o) {
        rd_ptr_q := rd_ptr_q + 1.U
    }
    when(io.push_i && io.accept_o && !(io.pop_i && io.valid_o)) {
        count_q := count_q + 1.U
    } .elsewhen(!(io.push_i && io.accept_o) && io.pop_i && io.valid_o) {
        count_q := count_q - 1.U
    }

//-------------------------------------------------------------------
// Combinatorial
//-------------------------------------------------------------------
    io.out_data := ram_q(rd_ptr_q)
    io.accept_o := count_q =/= DEPTH.U
    io.valid_o  := count_q =/= 0.U
}
