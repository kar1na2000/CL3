package cl3

import chisel3._
import chisel3.util._

class CL3Top extends Module with CL3Config {

  val io = IO(new Bundle {
    val extIrq   = Input(Bool())
    val timerIrq = Input(Bool())
    val master   = new SimpleAXI4MasterBundle(AddrWidth, DataWidth, 4)
  })

  val core = Module(new CL3Core)

  if (SimMemOption == "SoC") {
    val icache = Module(new CL3ICache)
    icache.io.in.req.valid          := core.io.imem.req.valid
    core.io.imem.req.ready          := icache.io.in.req.ready
    icache.io.in.req.bits.addr      := core.io.imem.req.bits.addr
    icache.io.in.req.bits.cacheable := false.B
    icache.io.in.req.bits.mask      := 0.U
    icache.io.in.req.bits.size      := 2.U
    icache.io.in.req.bits.wdata     := 0.U
    icache.io.in.req.bits.wen       := false.B

    icache.io.in.resp.ready := true.B

    core.io.imem.resp.valid      := icache.io.in.resp.valid
    core.io.imem.resp.bits.err   := icache.io.in.resp.bits.err
    core.io.imem.resp.bits.rdata := icache.io.in.resp.bits.rdata

    val dcache = Module(new CL3DCache)
    dcache.io.cpu.req.valid          := core.io.dmem.req.valid
    core.io.dmem.req.ready           := dcache.io.cpu.req.ready
    dcache.io.cpu.req.bits.addr      := core.io.dmem.req.bits.addr
    dcache.io.cpu.req.bits.cacheable := core.io.dmem.req.bits.cacheable
    dcache.io.cpu.req.bits.mask      := core.io.dmem.req.bits.mask
    dcache.io.cpu.req.bits.size      := 2.U
    dcache.io.cpu.req.bits.wdata     := core.io.dmem.req.bits.wdata
    dcache.io.cpu.req.bits.wen       := core.io.dmem.req.bits.wen

    dcache.io.cpu.resp.ready := true.B

    core.io.dmem.resp.valid      := dcache.io.cpu.resp.valid
    core.io.dmem.resp.bits.err   := dcache.io.cpu.resp.bits.err
    core.io.dmem.resp.bits.rdata := dcache.io.cpu.resp.bits.rdata

    val req_xbar = Module(new SimpleReqArbiter)
    req_xbar.io.ifuReq <> icache.io.out.req
    req_xbar.io.lsuReq <> dcache.io.rd.req

    val resp_xbar = Module(new SimpleRespArbiter)
    resp_xbar.io.ifu <> icache.io.out.resp
    resp_xbar.io.lsu <> dcache.io.rd.resp

    val bridge = Module(new Cache2AXI4Bridge)
    bridge.io.rdReq <> req_xbar.io.outReq
    bridge.io.rdResp <> resp_xbar.io.in
    bridge.io.rdReqIsIFU := req_xbar.io.isIFU
    resp_xbar.io.isIFU   := bridge.io.rdReqIsIFU

    val wr_buffer = Module(new CacheWriteBuffer)
    wr_buffer.io.cache <> dcache.io.wr
    wr_buffer.io.req <> bridge.io.wrReq
    wr_buffer.io.resp <> bridge.io.wrResp

    // TODO: actually we don't need this converter

    val converter = Module(new AXIWidthConverter)
    converter.io.in <> bridge.io.axi4
    converter.io.out <> io.master
  } else {

    val imem = Module(new MemHelper)
    imem.io.clock          := clock
    imem.io.reset          := reset
    imem.io.req.bits       := core.io.imem.req.bits
    imem.io.req.valid      := core.io.imem.req.valid
    core.io.imem.req.ready := imem.io.req.ready

    core.io.imem.resp.bits  := imem.io.resp.bits
    core.io.imem.resp.valid := imem.io.resp.valid
    imem.io.resp.ready      := true.B

    val dmem = Module(new MemHelper)
    dmem.io.clock := clock
    dmem.io.reset := reset
    dmem.io.req <> core.io.dmem.req

    core.io.dmem.resp.bits  := dmem.io.resp.bits
    core.io.dmem.resp.valid := dmem.io.resp.valid
    dmem.io.resp.ready      := true.B

    io.master <> DontCare

  }
}
