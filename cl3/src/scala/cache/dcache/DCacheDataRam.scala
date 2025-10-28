package cl3

import chisel3._
import chisel3.util._
import cl3.SramAddr._

class dcache_core_data_ramDev(p: DCacheParams) extends Module {
    val io = IO(new DCacheDataRamIO(p))

    val dataBits     = p.dataW
    val byteCount    = dataBits / 8
    val depthPerBank = 1 << (p.dataIdxW - p.bankSelBits)
    val banks        = p.banks

    val memBanks = Seq.fill(banks) {
        SyncReadMem(depthPerBank, Vec(byteCount, UInt(8.W)))
    }
    // 地址拆分
    val p0_bank = bankSel(io.p0.addr)   // UInt
    val p0_bank_reg = RegNext(p0_bank)
    val p0_row  = rowAddr(io.p0.addr)   // UInt

    val p1_wen   = io.p1.wstrb.orR
    val p1_bank  = bankSel(io.p1.addr)
    val p1_row   = rowAddr(io.p1.addr)

    val p1_wdata_bytes = VecInit(Seq.tabulate(byteCount)(i =>
        io.p1.wdata(8 * (i + 1) - 1, 8 * i)
    ))
    val p1_wmask_bytes = io.p1.wstrb // UInt(byteCount.W)

    // ---------------- p0：同步读（无旁路） ----------------
    val p0_rdata_q = WireDefault(0.U(dataBits.W))
    val p0_raw_vec = Wire(Vec(byteCount, UInt(8.W)))
    p0_raw_vec := VecInit(Seq.fill(byteCount)(0.U(8.W)))

    val doutVec = Wire(Vec(memBanks.length, UInt(dataBits.W)))
    for (i <- 0 until memBanks.length) {
      val rdBytes = memBanks(i).read(p0_row, true.B)
      doutVec(i) := rdBytes.asUInt
    }
    p0_rdata_q := doutVec(p0_bank_reg)
    io.p0.rdata := p0_rdata_q
    io.p1.rdata := p0_rdata_q

    when (p1_wen) {
    for (b <- 0 until banks) {
        when (p1_bank === b.U) {
            val wmaskVec = VecInit(Seq.tabulate(byteCount)(i => p1_wmask_bytes(i)))
                memBanks(b).write(p1_row, p1_wdata_bytes, wmaskVec)
            }
        }
    }

}

// class dcache_core_data_ramDev(p: DCacheParams) extends Module {
//     val io = IO(new DCacheDataRamIO(p))
//     val dataBits     = p.dataW
//     val depthPerBank = 1 << (p.dataIdxW - p.bankSelBits)
//     val banks = p.banks
//     val memBanks = Seq.fill(banks) {
//         SyncReadMem(depthPerBank, Vec(dataBits, Bool()))
//     }

//     // UInt <-> Vec[Bool]
//     private def u2bVec(x: UInt, width: Int): Vec[Bool] = VecInit(Seq.tabulate(width)(i => x(i)))
//     private def bVec2u(v: Vec[Bool]): UInt = v.asUInt


//     // ---------------- p0: 只读 ----------------
//     val p0_bank = bankSel(io.p0.addr)
//     val p0_row  = rowAddr(io.p0.addr)

//     // ---------------- p1: 只写 ----------------
//     val p1_wen        = io.p1.wstrb.orR
//     val p1_bank       = bankSel(io.p1.addr)
//     val p1_row        = rowAddr(io.p1.addr)
//     val p1_wmaskBits  = io.p1.wstrb
//     val p1_wdataBits  = io.p1.wdata

//     val bankConflict = p1_wen && (p0_bank === p1_bank)

//     val wbuf = Seq.fill(banks)(WB(p))

//     val p0_rdata_q = Reg(UInt(dataBits.W))
//     p0_rdata_q := 0.U

//     for (b <- 0 until banks) {
//         when(p0_bank === b.U) {
//             val rdVec = memBanks(b).read(p0_row, true.B)
//             p0_rdata_q := bVec2u(rdVec)
//         }
//     }
//     io.p0.rdata := p0_rdata_q
//     io.p1.rdata := p0_rdata_q

//     for (b <- 0 until banks) {
//         when(wbuf(b).valid && !(p0_bank === b.U)) {
//             memBanks(b).write(
//                 wbuf(b).row,
//                 u2bVec(wbuf(b).data, dataBits),
//                 u2bVec(wbuf(b).mask, dataBits)
//             )
//             wbuf(b).valid := false.B
//         }
//     }

//     when(p1_wen) {
//         for(b <- 0 until banks){
//             when(b.U === p1_bank){
//                 val canWriteNow = !(p0_bank === p1_bank) && !wbuf(b).valid
//                 val conflict    = (p0_bank === p1_bank)
//                 when(canWriteNow) {
//                     memBanks(b).write(
//                         p1_row,
//                         u2bVec(p1_wdataBits, dataBits),
//                         u2bVec(p1_wmaskBits, dataBits)
//                     )
//                 }.otherwise {
//                     when(!wbuf(b).valid) {
//                         wbuf(b).valid := true.B
//                         wbuf(b).row   := p1_row
//                         wbuf(b).data  := p1_wdataBits
//                         wbuf(b).mask  := p1_wmaskBits
//                     }.otherwise {
//                         assert(false.B, "Write buffer overflow on Data RAM bank; upstream must avoid back-to-back conflicts.")
//                     }
//                 }
//             }

//         }

//     }
// }

class dcache_core_data_ram(p: DCacheParams) extends Module {
  val io = IO(new DCacheDataRamIO(p))

  if (p.useMacro) {
    val m = Module(new DCacheDataRamMacro(p))
    m.io <> io
  } else {
    val m = Module(new dcache_core_data_ramDev(p))
    m.io <> io
  }
}