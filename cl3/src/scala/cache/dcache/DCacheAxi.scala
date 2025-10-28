package cl3

import chisel3._
import chisel3.util._

class dcache_axi(p: DCacheParams) extends Module {
    val io = IO(new DCacheAxiIO(p))

    val (req, resp, ar, aw, w, r, b) = (io.pmem.req, io.pmem.resp, io.axi.ar, io.axi.aw, io.axi.w, io.axi.r, io.axi.b)
    dontTouch(ar)
    dontTouch(r)
    dontTouch(aw)
    dontTouch(w)
    dontTouch(b)
    val u_axi = Module(new dcache_axi_axi(p))
    val accept_w = u_axi.io.pmem.resp.accept

// Push on transaction and other FIFO not full
    val req_push_w    = req.rd || req.wr.orR
    val req_data_in_w = Cat(req.len, req.rd, req.wr, req.writeData, req.addr)

    val u_req = Module(new DCacheAxiFifo(WIDTH = (p.addrW + p.dataW + p.lenW + p.wstrbW + 1), DEPTH = 2, ADDR_W = 1))
    u_req.io.push_i   := req_push_w
    u_req.io.in_data  := req_data_in_w
    u_req.io.pop_i    := accept_w

    val req_accept_w = u_req.io.accept_o
    val req_valid_w  = u_req.io.valid_o
    val req_w        = u_req.io.out_data
    
    val res_accept_w = Wire(Bool())
    val req_can_issue_w = req_valid_w && res_accept_w
    val req_is_read_w   = Mux(req_can_issue_w, req_w(p.addrW + p.dataW + p.wstrbW), false.B)
    val req_is_write_w  = Mux(req_can_issue_w, !req_w(p.addrW + p.dataW + p.wstrbW), false.B)
    val req_len_w       = req_w(p.addrW + p.dataW + p.lenW + p.wstrbW, p.addrW + p.dataW + p.wstrbW + 1)

//-------------------------------------------------------------
// Write burst tracking
//-------------------------------------------------------------  
    val req_cnt_q = RegInit(0.U(p.lenW.W))

    when(req_is_write_w && req_cnt_q === 0.U && req_len_w =/= 0.U && accept_w){
        req_cnt_q := req_len_w - 1.U
    } .elsewhen(req_cnt_q =/= 0.U && accept_w && req_is_write_w) {
        req_cnt_q := req_cnt_q - 1.U
    }

    val req_last_w = (req_is_write_w && req_cnt_q === 0.U && req_len_w === 0.U)

//-------------------------------------------------------------
// Response tracking
//-------------------------------------------------------------
// Push on transaction and other FIFO not full
    val res_push_w = (req_is_read_w && accept_w ) || (req_is_write_w && req_last_w && accept_w)

// Pop on last tick of burst
    val resp_pop_w = (b.valid) || (r.bits.last && r.valid)

    val resp_outstanding_q = RegInit(0.U(2.W))

    val res_valid_w = Wire(Bool())

    when((res_push_w & res_accept_w) & !(resp_pop_w & res_valid_w)) {
        resp_outstanding_q := resp_outstanding_q + 1.U
    } .elsewhen(!(res_push_w & res_accept_w) & (resp_pop_w & res_valid_w)) {
        resp_outstanding_q := resp_outstanding_q - 1.U
    }

    res_valid_w  := resp_outstanding_q =/= 0.U
    res_accept_w := resp_outstanding_q =/= 2.U 

    val (u_axi_req, u_axi_resp, u_axi_in_ar, u_axi_in_aw, u_axi_in_w, u_axi_in_r, u_axi_in_b) = (u_axi.io.pmem.req, u_axi.io.pmem.resp, u_axi.io.in_axi.ar, u_axi.io.in_axi.aw, u_axi.io.in_axi.w, u_axi.io.in_axi.r, u_axi.io.in_axi.b)
    u_axi_in_b.bits := DontCare
    u_axi_in_r.bits := DontCare
    u_axi_in_ar := DontCare
    u_axi_in_aw := DontCare
    u_axi_in_w := DontCare

    val bvalid_w = u_axi_in_b.valid
    val rvalid_w = u_axi_in_r.valid
    val bresp_w  = u_axi_in_b.bits.resp
    val rresp_w  = u_axi_in_r.bits.resp

    resp.ack := bvalid_w || rvalid_w
    resp.accept := req_accept_w
    resp.error  := Mux(bvalid_w, (bresp_w =/= 0.U), (rresp_w =/= 0.U))
    // u_axi_in_w.bits := DontCare
    // u_axi_in_ar.bits := DontCare

    ar <> u_axi.io.out_axi.ar
    aw <> u_axi.io.out_axi.aw
    w  <> u_axi.io.out_axi.w
    u_axi.io.out_axi.b <> b
    u_axi.io.out_axi.r <> r


    u_axi_req.valid := req_can_issue_w
    u_axi_req.wr := req_is_write_w
    u_axi_in_w.bits.data := req_w(p.addrW + p.dataW - 1, p.addrW)
    u_axi_in_w.bits.strb := req_w(p.addrW + p.dataW + p.wstrbW - 1, p.addrW + p.dataW)
    u_axi_req.addr  := Cat(req_w(p.addrW - 1, 2), 0.U(2.W))
    u_axi_req.id    := p.axi_id.U
    u_axi_req.len   := req_len_w
    u_axi_req.burst := 1.U
    u_axi_in_b.ready := 1.U
    u_axi_in_r.ready := 1.U
    resp.readData     := u_axi_in_r.bits.data

}


class DCacheAxiFifo(WIDTH: Int = 8, DEPTH: Int = 4, ADDR_W: Int = 2) extends Module {
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
    when((io.push_i && io.accept_o) && !(io.pop_i && io.valid_o)) {
        count_q := count_q + 1.U
    } .elsewhen(!(io.push_i && io.accept_o) && (io.pop_i && io.valid_o)) {
        count_q := count_q - 1.U
    }

//-------------------------------------------------------------------
// Combinatorial
//-------------------------------------------------------------------
    io.out_data := ram_q(rd_ptr_q)
    io.accept_o := count_q =/= DEPTH.U
    io.valid_o  := count_q =/= 0.U
}

