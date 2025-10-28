package cl3

import chisel3._
import chisel3.util._
import cl3.SramAddr._

//-----------------------------------------------------------------
// Single Port RAM 8KB
// Mode: Read First
//-----------------------------------------------------------------
class ICacheDataRamDev(p: ICacheParams) extends Module {
    val io = IO(new ICacheDataRamIO(p))
    
    val dataBits     = p.dataRamDataBits
    // val depthPerBank = 1 << (p.dataRamIdxBits - p.bankSelBits)
    // Synchronous write
    val memBanks = Seq.fill(p.banks) { 
        SyncReadMem(1 << p.dataRamIdxBits, UInt(dataBits.W)) 
    }

    val bank = bankSel(io.addr)
    val bank_reg = RegNext(bank)

    val row  = rowAddr(io.addr)
    val wen  = io.wr
    val wdata= io.din
    val rdata = WireDefault(0.U(p.dataRamDataBits.W))
    for(i <- 0 until p.banks){
        when(i.U === bank){
            when (wen) {
                memBanks(i).write(row, wdata)
            }
        }
    }
    val doutVec = Wire(Vec(memBanks.length, UInt(p.dataRamDataBits.W)))
    for (i <- 0 until memBanks.length) {
      doutVec(i) := memBanks(i).read(row, true.B)
    }
    rdata := doutVec(bank_reg)
    io.dout := rdata
    // val r_q   = rdata
    // io.dout := memBanks(bank).read(row, true.B)
}

class ICacheDataRam(p: ICacheParams)
    extends Module {
  val io = IO(new ICacheDataRamIO(p))

  if (p.useMacro) {
    val m = Module(new ICacheDataRamMacro(p))
    m.io <> io
  } else {
    val m = Module(new ICacheDataRamDev(p))
    m.io <> io
  }
}
