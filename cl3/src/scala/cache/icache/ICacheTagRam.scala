package cl3

import chisel3._
import chisel3.util._

//-----------------------------------------------------------------
// Single Port RAM 0KB
// Mode: Read First
//-----------------------------------------------------------------
class ICacheTagRamDev(p: ICacheParams) extends Module {
    val io = IO(new ICacheTagRamIO(p))

    // Synchronous write
    val mem   = SyncReadMem(1 << p.tagRamIdxBits, UInt(p.tagRamDataBits.W))
    val rdata = mem.read(io.addr, true.B)
    val r_q   = rdata

    when (io.wr) {
        mem.write(io.addr, io.din)
    }
    io.dout := r_q
}

class ICacheTagRam(p: ICacheParams)
    extends Module {
  val io = IO(new ICacheTagRamIO(p))

  if (p.useMacro) {
    val m = Module(new ICacheTagRamMacro(p))
    m.io <> io
  } else {
    val m = Module(new ICacheTagRamDev(p))
    m.io <> io
  }
}