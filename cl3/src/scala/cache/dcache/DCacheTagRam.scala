package cl3

import chisel3._
import chisel3.util._

class dcache_core_tag_ramDev(p: DCacheParams) extends Module {
    val io = IO(new DCacheTagRamIO(p))

//-----------------------------------------------------------------
// Tag RAM 0KB (256 x 21)
// Mode: Write First
//-----------------------------------------------------------------
    // val addr = Mux(io.p1.wen, io.p1.addr, io.p0.addr)
    val mem = SyncReadMem(p.sets, UInt((p.tagEntryW).W))

    val ram_read0_q = mem.read(io.p0.addr, true.B)
    io.p0.rdata := ram_read0_q

    when(io.p1.wen) {
        mem.write(io.p1.addr, io.p1.wdata)
    }
}

class dcache_core_tag_ram(p: DCacheParams) extends Module {
  val io = IO(new DCacheTagRamIO(p))

  if (p.useMacro) {
    val m = Module(new DCacheTagRamMacro(p))
    m.io <> io
  } else {
    val m = Module(new dcache_core_tag_ramDev(p))
    m.io <> io
  }
}