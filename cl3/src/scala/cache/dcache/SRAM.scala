package cl3

import chisel3._
import chisel3.util._
import chisel3.experimental._
import cl3.SramAddr._

// SRAM
class S55NLLG1PH_X128Y2D21(val addrBits: Int, val dataBits: Int)
    extends ExtModule with HasExtModuleResource {

    val CLK = IO(Input(Clock()))
    val CEN = IO(Input(Bool()))
    val WEN = IO(Input(Bool()))
    val A   = IO(Input(UInt(addrBits.W)))
    val D   = IO(Input(UInt(dataBits.W)))
    val Q   = IO(Output(UInt(dataBits.W)))

    addResource("/vsrc/S55NLLG1PH_X128Y2D21.v")
}

class S55NLLG1PH_X32Y2D21(val addrBits: Int, val dataBits: Int)
    extends ExtModule with HasExtModuleResource {

    val CLK = IO(Input(Clock()))
    val CEN = IO(Input(Bool()))
    val WEN = IO(Input(Bool()))
    val A   = IO(Input(UInt(addrBits.W)))
    val D   = IO(Input(UInt(dataBits.W)))
    val Q   = IO(Output(UInt(dataBits.W)))

    addResource("/vsrc/S55NLLG1PH_X32Y2D21.v")
}

class S55NLLG1PH_X128Y4D32_BW(val addrBits:  Int, val dataBits:  Int)
    extends ExtModule with HasExtModuleResource {

  val CLK = IO(Input(Clock()))
  val CEN = IO(Input(Bool()))
  val WEN = IO(Input(Bool()))
  val A   = IO(Input(UInt(addrBits.W)))
  val D   = IO(Input(UInt(dataBits.W)))
  val Q   = IO(Output(UInt(dataBits.W)))
  val BWEN= IO(Input(UInt(dataBits.W)))

  addResource("/vsrc/S55NLLG1PH_X128Y4D32_BW.v")
}


// class DCacheTagRamMacro(p: DCacheParams) extends Module {
//     val io = IO(new DCacheTagRamIO(p))

//     val sram = Module(new S55NLLG1PH_X128Y2D21(
//         addrBits = p.tagIdxW,
//         dataBits = p.tagEntryW
//     ))

//     sram.CLK := clock
//     sram.CEN := false.B
//     sram.WEN := !io.p1.wen
//     sram.A   := Mux(io.p1.wen, io.p1.addr, io.p0.addr)
//     sram.D   := io.p1.wdata
//     val q = sram.Q
//     io.p0.rdata := q

// }

class DCacheTagRamMacro(p: DCacheParams) extends Module {
    val io = IO(new DCacheTagRamIO(p))

    val banks = p.banks
    val sramBanks = Seq.fill(banks)(Module(new S55NLLG1PH_X32Y2D21(
        addrBits  = p.tagIdxW,
        dataBits  = p.tagEntryW
    )))

    val p0_bank = bankSel(io.p0.addr)
    val p0_bank_reg = RegNext(p0_bank)
    val p0_row  = rowAddr(io.p0.addr)

    val p1_wen        = io.p1.wen
    val p1_bank       = bankSel(io.p1.addr)
    val p1_row        = rowAddr(io.p1.addr)
    val p1_wdataBits  = io.p1.wdata
    
    val bankConflict = p1_wen && (p0_bank === p1_bank) && (p0_row =/= p1_row)
    dontTouch(bankConflict)

    for (b <- 0 until banks) {
        val isWriteThisBank = p1_wen && (p1_bank === b.U)
        sramBanks(b).CLK := clock
        sramBanks(b).CEN := false.B
        sramBanks(b).WEN := !isWriteThisBank
        sramBanks(b).A   := Mux(isWriteThisBank, p1_row, p0_row)
        sramBanks(b).D   := p1_wdataBits
    }
    
    val doutVec = Wire(Vec(banks, UInt(p.tagEntryW.W)))
    for (i <- 0 until banks) {
      doutVec(i) := sramBanks(i).Q
    }
    io.p0.rdata := doutVec(p0_bank_reg)

}

class DCacheDataRamMacro(p: DCacheParams) extends Module {
    val io = IO(new DCacheDataRamIO(p))

    val dataBits     = p.dataW
    val banks = p.banks
    val sramBanks = Seq.fill(banks)(Module(new S55NLLG1PH_X128Y4D32_BW(
        addrBits  = p.dataIdxW,
        dataBits  = p.dataW
    )))

    val p0_bank = bankSel(io.p0.addr)
    val p0_bank_reg = RegNext(p0_bank)
    val p0_row  = rowAddr(io.p0.addr)

    val p1_wen        = io.p1.wstrb.orR
    val p1_bank       = bankSel(io.p1.addr)
    val p1_row        = rowAddr(io.p1.addr)
    val p1_wmaskBits  = byteMaskToBitMask(io.p1.wstrb, p.dataW)
    val p1_wdataBits  = io.p1.wdata
    
    val bankConflict = p1_wen && (p0_bank === p1_bank) && (p0_row =/= p1_row)
    dontTouch(bankConflict)

    for (b <- 0 until banks) {
        val isWriteThisBank = p1_wen && (p1_bank === b.U)
        sramBanks(b).CLK := clock
        sramBanks(b).CEN := false.B
        sramBanks(b).WEN := !isWriteThisBank
        sramBanks(b).A   := Mux(isWriteThisBank, p1_row, p0_row)
        sramBanks(b).D   := p1_wdataBits
        sramBanks(b).BWEN:= Mux(isWriteThisBank, p1_wmaskBits, Fill(p.dataW, 1.U(1.W)))
    }
    
    val doutVec = Wire(Vec(banks, UInt(p.dataW.W)))
    for (i <- 0 until banks) {
      doutVec(i) := sramBanks(i).Q
    }
    io.p0.rdata := doutVec(p0_bank_reg)
    io.p1.rdata := doutVec(p0_bank_reg)
}

