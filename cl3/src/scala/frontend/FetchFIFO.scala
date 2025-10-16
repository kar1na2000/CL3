package cl3

import chisel3._
import chisel3.util._

trait FetchFIFOConfig {
  val FIFOWidth:   Int = 64
  val FIFODepth:   Int = 2
  val opInfoWidth: Int = 10
}

class FetchFIFO() extends Module with FetchFIFOConfig {
  val io = IO(new Bundle {
    val flush = Input(Bool())
    val in    = Flipped(Decoupled(new FetchFIFOInput))
    val out   = Decoupled(Vec(2, new FetchFIFOOutput))
  })

  class FIFOEntry() extends Bundle {
    val pc     = UInt(32.W)
    val inst   = UInt(FIFOWidth.W)
    val info0  = UInt(opInfoWidth.W)
    val info1  = UInt(opInfoWidth.W)
  }

  val entry_vec = RegInit(VecInit(Seq.fill(FIFODepth)(0.U.asTypeOf(new FIFOEntry))))
  val rd_ptr_q  = RegInit(0.U(1.W))
  val wr_ptr_q  = RegInit(0.U(1.W))
  val count_q   = RegInit(0.U(2.W))

  val is_full  = (count_q === FIFODepth.U)
  val is_empty = (count_q === 0.U)

  io.in.ready := !is_full && !io.flush

  val head = entry_vec(rd_ptr_q)

  io.out.valid := !is_empty

  val push = io.in.fire
  val pop  = io.out.fire

  when(io.flush) {
    wr_ptr_q := 0.U
    for (i <- 0 until FIFODepth) {
      entry_vec(i) := 0.U.asTypeOf(new FIFOEntry)
    }

  }.elsewhen(push) {
    entry_vec(wr_ptr_q).pc     := io.in.bits.pc
    entry_vec(wr_ptr_q).inst   := io.in.bits.data
    entry_vec(wr_ptr_q).info0  := io.in.bits.info0
    entry_vec(wr_ptr_q).info1  := io.in.bits.info1

    wr_ptr_q                   := wr_ptr_q + 1.U
  }

  when(io.flush) {
    rd_ptr_q := 0.U
  }.elsewhen(pop) {
    rd_ptr_q := rd_ptr_q + 1.U
  }

  when(io.flush) {
    count_q := 0.U
  }.elsewhen(push && !pop) {
    count_q := count_q + 1.U
  }.elsewhen(!push && pop) {
    count_q := count_q - 1.U
  }

  val rd_entry = entry_vec(rd_ptr_q)

  // TODO: We should change PC logic when we support C extension
  io.out.bits(0).pc   := Cat(rd_entry.pc(31, 3), 0.U(3.W))
  io.out.bits(0).inst := rd_entry.inst((FIFOWidth / 2) - 1, 0)
  io.out.bits(0).info := rd_entry.info0

  io.out.bits(1).pc   := Cat(rd_entry.pc(31, 3), "b100".U(3.W))
  io.out.bits(1).inst := rd_entry.inst(FIFOWidth - 1, FIFOWidth / 2)
  io.out.bits(1).info := rd_entry.info1
}

