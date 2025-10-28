package cl3

import chisel3._
import chisel3.util._

class AxiArbiter(implicit p: AXI4Params) extends Module {
    val io = IO(new AxiArbiterIO())

    val (ic_ar, ic_aw, ic_w, ic_r, ic_b) = (
        io.icache_axi.ar, io.icache_axi.aw, io.icache_axi.w,
        io.icache_axi.r,  io.icache_axi.b
    )
    // unused signals
    ic_aw := DontCare
    ic_w  := DontCare
    ic_b  := DontCare
    val (dc_ar, dc_aw, dc_w, dc_r, dc_b) = (
        io.dcache_axi.ar, io.dcache_axi.aw, io.dcache_axi.w,
        io.dcache_axi.r,  io.dcache_axi.b
    )

    val (mem_ar, mem_aw, mem_w, mem_r, mem_b) = (
        io.mem_axi.ar, io.mem_axi.aw, io.mem_axi.w,
        io.mem_axi.r,  io.mem_axi.b
    )
    val arbActive     = RegInit(false.B)  // arbiter active
    val axiReadBusy   = RegInit(false.B)  // AXI 总线是否处于读事务中 (read channel busy)
    val lastServedD   = RegInit(false.B)  // 上次服务的是谁: 0=ICache, 1=DCache

    val dcacheGrantReg = RegInit(false.B) // 保存上周期 DCache 是否被仲裁到
    val dcacheGranted  = WireDefault(dcacheGrantReg) // 当前是否轮到 DCache
    dcacheGrantReg := dcacheGranted

    when(!axiReadBusy) {
        // when arbiter is idle, decide who to serve next
        axiReadBusy := ic_ar.valid || dc_ar.valid
        when(ic_ar.valid && dc_ar.valid) {
            dcacheGranted := ~lastServedD
            lastServedD := ~lastServedD
        } .elsewhen(ic_ar.valid) {
            dcacheGranted := false.B
        } .elsewhen(dc_ar.valid){
            dcacheGranted := true.B
        }
    }

    mem_ar <> Mux1H(Seq(
        dcacheGranted -> dc_ar,
        !dcacheGranted -> ic_ar
    ))

    // ar channel
    ic_ar.ready := Mux(!dcacheGranted, mem_ar.ready, false.B)
    dc_ar.ready := Mux(dcacheGranted, mem_ar.ready, false.B)

    // resp channel
    ic_r <> mem_r
    ic_r.valid := Mux(!dcacheGrantReg, mem_r.valid, false.B)
    ic_r.bits.last := Mux(!dcacheGrantReg, mem_r.bits.last, false.B)
    dc_r <> mem_r
    dc_r.valid := Mux(dcacheGrantReg, mem_r.valid, false.B)
    dc_r.bits.last := Mux(dcacheGrantReg, mem_r.bits.last, false.B)


    // DCache write
    mem_aw <> dc_aw
    mem_w  <> dc_w
    dc_b  <> mem_b

    when(mem_r.valid && mem_r.ready && mem_r.bits.last) {
        axiReadBusy := false.B
    }
}