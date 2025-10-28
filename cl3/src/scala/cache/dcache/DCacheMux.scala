package cl3

import chisel3._
import chisel3.util._

class dcache_mux(p: DCacheParams) extends Module {
    val io = IO(new DCacheMuxIO(p))

    val (cpuReq, cpuResp, cacheReq, cacheResp, unReq, unResp) = (io.cpu.req, io.cpu.resp, io.backs.cached.req, io.backs.cached.resp, io.backs.uncached.req, io.backs.uncached.resp)

    val hold_w = Wire(Bool())
    val cache_access_q = RegInit(false.B)

    cacheReq <> cpuReq
    cacheReq.rd         := Mux(cpuReq.cacheable & !hold_w, cpuReq.rd , 0.U)
    cacheReq.wr         := Mux(cpuReq.cacheable & !hold_w, cpuReq.wr , 0.U)
    cacheReq.invalidate := Mux(cpuReq.cacheable & !hold_w, cpuReq.invalidate , 0.U)
    cacheReq.writeback  := Mux(cpuReq.cacheable & !hold_w, cpuReq.writeback , 0.U)
    cacheReq.flush      := Mux(cpuReq.cacheable & !hold_w, cpuReq.flush , 0.U) 

    unReq <> cpuReq
    unReq.rd         := Mux(!cpuReq.cacheable & !hold_w, cpuReq.rd , 0.U)
    unReq.wr         := Mux(!cpuReq.cacheable & !hold_w, cpuReq.wr , 0.U)
    unReq.invalidate := Mux(!cpuReq.cacheable & !hold_w, cpuReq.invalidate , 0.U)
    unReq.writeback  := Mux(!cpuReq.cacheable & !hold_w, cpuReq.writeback , 0.U)
    unReq.flush      := Mux(!cpuReq.cacheable & !hold_w, cpuReq.flush , 0.U) 

    io.cpu.accept  := (Mux(cpuReq.cacheable, io.backs.cached.accept, io.backs.uncached.accept)) & !hold_w
    cpuResp.dataRd  := Mux(cache_access_q, cacheResp.dataRd, unResp.dataRd)
    cpuResp.ack     := Mux(cache_access_q, cacheResp.ack, unResp.ack)
    cpuResp.error   := Mux(cache_access_q, cacheResp.error, unResp.error)
    cpuResp.respTag := Mux(cache_access_q, cacheResp.respTag, unResp.respTag)

    // val request_w = cpuReq.rd | (cpuReq.rd && (cpuReq.wr =/= 0.U)) | cpuReq.flush | cpuReq.invalidate | cpuReq.writeback
    val request_w = cpuReq.rd | cpuReq.flush | cpuReq.invalidate | cpuReq.writeback

    val pending_q = RegInit(0.U(5.W))
    val pending_r = WireDefault(pending_q)

    when(request_w && io.cpu.accept && !cpuResp.ack){
        pending_r := pending_q + 1.U
    } .elsewhen(!(request_w && io.cpu.accept) && cpuResp.ack) {
        pending_r := pending_q - 1.U
    }

    pending_q := pending_r

    when(request_w && io.cpu.accept) {
        cache_access_q := cpuReq.cacheable
    }

    hold_w := (pending_q.orR) && (cache_access_q =/= cpuReq.cacheable)
    io.cacheActive := Mux(pending_q.orR, cache_access_q, cpuReq.cacheable)

}