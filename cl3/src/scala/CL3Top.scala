package cl3

import chisel3._
import chisel3.util._

class CL3Top extends Module with CL3Config {

  val io = IO(new Bundle {
    val extIrq   = Input(Bool())
    val timerIrq = Input(Bool())
    val master   = new SimpleAXI4MasterBundle(AddrWidth, DataWidth, 4)
  })

  implicit val axiP: AXI4Params = AXI4Params()
  val dp: DCacheParams = DCacheParams()
  val ip: ICacheParams = ICacheParams()
  val core = Module(new CL3Core)
  if (SimMemOption == "SoC") {
    val icache = Module(new ICache(ip))
    icache.io.cpu.req_rd            := core.io.imem.req.valid
    core.io.imem.req.ready          := icache.io.cpu.resp_accept
    icache.io.cpu.req_pc            := core.io.imem.req.bits.addr
    icache.io.cpu.req_flush := core.io.imem.req.bits.flush
    dontTouch(core.io.imem.req.bits.flush)
    dontTouch(icache.io.cpu.req_flush)
    icache.io.cpu.req_invalidate := core.io.imem.req.bits.invalidate

    core.io.imem.resp.valid      := icache.io.cpu.resp_valid
    core.io.imem.resp.bits.err   := icache.io.cpu.resp_error
    core.io.imem.resp.bits.rdata := icache.io.cpu.resp_inst

    val dcache = Module(new DCache(dp))
    dcache.io.cpu.req.rd             := core.io.dmem.req.valid
    core.io.dmem.req.ready           := dcache.io.cpu.accept
    dcache.io.cpu.req.addr           := core.io.dmem.req.bits.addr
    dcache.io.cpu.req.cacheable      := core.io.dmem.req.bits.cacheable
    dcache.io.cpu.req.wr             := core.io.dmem.req.bits.mask
    dcache.io.cpu.req.dataWr    := core.io.dmem.req.bits.wdata
    dcache.io.cpu.req.invalidate := false.B
    dcache.io.cpu.req.writeback  := false.B
    dcache.io.cpu.req.flush      := false.B
    dcache.io.amo := DontCare
    dcache.io.cpu.req.reqTag := DontCare
    // dcache.io.cpu.resp.ready := true.B

    core.io.dmem.resp.valid      := dcache.io.cpu.resp.ack
    core.io.dmem.resp.bits.err   := dcache.io.cpu.resp.error
    core.io.dmem.resp.bits.rdata := dcache.io.cpu.resp.dataRd

    val u_arbiter = Module(new AxiArbiter())
    u_arbiter.io.icache_axi <> icache.io.axi
    u_arbiter.io.dcache_axi <> dcache.io.axi

    // val converter = Module(new AXIWidthConverter)
    // ---------------- AW ----------------
    io.master.aw.valid                    := u_arbiter.io.mem_axi.aw.valid
    io.master.aw.bits.awaddr              := u_arbiter.io.mem_axi.aw.bits.addr
    io.master.aw.bits.awid                := u_arbiter.io.mem_axi.aw.bits.id
    io.master.aw.bits.awlen               := u_arbiter.io.mem_axi.aw.bits.len
    io.master.aw.bits.awsize              := u_arbiter.io.mem_axi.aw.bits.size
    io.master.aw.bits.awburst             := u_arbiter.io.mem_axi.aw.bits.burst
    io.master.aw.bits.awlock              := u_arbiter.io.mem_axi.aw.bits.lock
    io.master.aw.bits.awcache             := u_arbiter.io.mem_axi.aw.bits.cache
    io.master.aw.bits.awprot              := u_arbiter.io.mem_axi.aw.bits.prot
    u_arbiter.io.mem_axi.aw.ready         := io.master.aw.ready

    // ----------------  W ----------------
    io.master.w.valid                     := u_arbiter.io.mem_axi.w.valid
    io.master.w.bits.wdata                := u_arbiter.io.mem_axi.w.bits.data
    io.master.w.bits.wstrb                := u_arbiter.io.mem_axi.w.bits.strb
    io.master.w.bits.wlast                := u_arbiter.io.mem_axi.w.bits.last
    u_arbiter.io.mem_axi.w.ready          := io.master.w.ready

    // ----------------  B ----------------
    u_arbiter.io.mem_axi.b.valid          := io.master.b.valid
    u_arbiter.io.mem_axi.b.bits.resp      := io.master.b.bits.bresp
    u_arbiter.io.mem_axi.b.bits.id        := io.master.b.bits.bid
    io.master.b.ready                     := u_arbiter.io.mem_axi.b.ready

    // ---------------- AR ----------------
    io.master.ar.valid                    := u_arbiter.io.mem_axi.ar.valid
    io.master.ar.bits.araddr              := u_arbiter.io.mem_axi.ar.bits.addr
    io.master.ar.bits.arid                := u_arbiter.io.mem_axi.ar.bits.id
    io.master.ar.bits.arlen               := u_arbiter.io.mem_axi.ar.bits.len
    io.master.ar.bits.arsize              := u_arbiter.io.mem_axi.ar.bits.size
    io.master.ar.bits.arburst             := u_arbiter.io.mem_axi.ar.bits.burst
    io.master.ar.bits.arlock              := u_arbiter.io.mem_axi.ar.bits.lock
    io.master.ar.bits.arcache             := u_arbiter.io.mem_axi.ar.bits.cache
    io.master.ar.bits.arprot              := u_arbiter.io.mem_axi.ar.bits.prot
    u_arbiter.io.mem_axi.ar.ready         := io.master.ar.ready

    // ----------------  R ----------------
    u_arbiter.io.mem_axi.r.valid          := io.master.r.valid
    u_arbiter.io.mem_axi.r.bits.data      := io.master.r.bits.rdata
    u_arbiter.io.mem_axi.r.bits.resp      := io.master.r.bits.rresp
    u_arbiter.io.mem_axi.r.bits.last      := io.master.r.bits.rlast
    u_arbiter.io.mem_axi.r.bits.id        := io.master.r.bits.rid
    io.master.r.ready                     := u_arbiter.io.mem_axi.r.ready

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
