package cl3

import chisel3._
import chisel3.util._

class dcache_axi_axi(p: DCacheParams) extends Module {
    val io = IO(new DCacheAxitoAXiIO(p))

    val (req, resp, in_ar, in_aw, in_w, in_r, in_b) = (io.pmem.req, io.pmem.resp, io.in_axi.ar, io.in_axi.aw, io.in_axi.w, io.in_axi.r, io.in_axi.b)
    val (out_ar, out_aw, out_w, out_r, out_b) = (io.out_axi.ar, io.out_axi.aw, io.out_axi.w, io.out_axi.r, io.out_axi.b)
    resp := DontCare
    in_aw := DontCare
    in_w := DontCare
    in_ar := DontCare
    out_ar.bits  := 0.U.asTypeOf(io.out_axi.ar.bits)
    out_w.bits   := 0.U.asTypeOf(io.out_axi.w.bits)
    out_aw.bits  := 0.U.asTypeOf(io.out_axi.aw.bits)


//-------------------------------------------------------------
// Write Request
//-------------------------------------------------------------
    val awvalid_inhibit_q = RegInit(false.B)
    val wvalid_inhibit_q = RegInit(false.B)

    when(out_aw.fire && out_w.valid && !out_w.ready) {
        awvalid_inhibit_q := true.B
    } .elsewhen(out_aw.fire && (out_aw.bits.len =/= 0.U)) {
        awvalid_inhibit_q := true.B
    } .elsewhen(out_w.fire && out_w.bits.last) {
        awvalid_inhibit_q := false.B
    }

    when(out_w.fire && out_aw.valid && !out_aw.ready) {
        wvalid_inhibit_q := true.B
    } .elsewhen(out_aw.fire) {
        wvalid_inhibit_q := false.B
    }

    out_aw.valid := req.valid & req.wr & ~awvalid_inhibit_q
    out_aw.bits.addr  := req.addr
    out_aw.bits.id    := req.id
    out_aw.bits.len   := req.len
    out_aw.bits.burst := req.burst
    out_aw.bits.size  := (log2Ceil(p.axi.axiDataBits / 8)).U
    out_ar.bits.cache := "b1010".U(4.W) // Cacheable

//-------------------------------------------------------------
// Write burst tracking
//-------------------------------------------------------------
    val req_cnt_q = RegInit(0.U(p.lenW.W))
    when(out_aw.fire) {
        when(~out_w.ready && !wvalid_inhibit_q){
            req_cnt_q := out_aw.bits.len + 1.U
        } .otherwise {
            req_cnt_q := out_aw.bits.len
        }
    } .elsewhen(req_cnt_q =/= 0.U && out_w.fire) {
        req_cnt_q := req_cnt_q - 1.U
    }

    // out_w.valid := req.valid & req.wr & ~wvalid_inhibit_q
    // out_w.bits.data := req.writeData
    // out_w.bits.strb := Fill(p.dataW/8, 1.U(1.W))
    // out_w.bits.last := (wburst_cnt_q === 0.U)

    val wlast_w = (out_aw.valid && out_aw.bits.len === 0.U) || (req_cnt_q === 1.U)

//-------------------------------------------------------------
// Write data skid buffer
//-------------------------------------------------------------
    val buf_valid_q = RegInit(false.B)
    when(out_w.valid && !out_w.ready && out_aw.fire){
        buf_valid_q := true.B
    } .elsewhen(out_w.ready) {
        buf_valid_q := false.B
    }

    val buf_q = RegInit(0.U((p.dataW + p.wstrbW + 1).W))
    buf_q := Cat(out_w.bits.last, out_w.bits.strb, out_w.bits.data)

    out_w.valid := buf_valid_q || (req.valid & req.wr & ~wvalid_inhibit_q)
    out_w.bits.data := Mux(buf_valid_q, buf_q(p.dataW-1, 0), in_w.bits.data)
    out_w.bits.strb := Mux(buf_valid_q, buf_q(p.dataW + p.wstrbW -1, p.dataW), in_w.bits.strb)
    out_w.bits.last := Mux(buf_valid_q, buf_q(p.dataW + p.wstrbW), wlast_w)

    in_b <> out_b

//-------------------------------------------------------------
// Read Request
//-------------------------------------------------------------
    out_ar.valid      := req.valid & ~req.wr
    out_ar.bits.addr  := req.addr
    out_ar.bits.id    := req.id
    out_ar.bits.len   := req.len
    out_ar.bits.burst := req.burst
    out_ar.bits.size  := (log2Ceil(p.axi.axiDataBits / 8)).U
    out_ar.bits.cache := "b0110".U(4.W) // Cacheable

    in_r <> out_r

//-------------------------------------------------------------
// Accept logic
//-------------------------------------------------------------

    resp.accept := ( out_aw.fire ) || ( out_w.fire && ~buf_valid_q ) || ( out_ar.fire )

}