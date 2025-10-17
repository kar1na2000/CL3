package cl3

import chisel3._
import chisel3.util._

trait FetchFIFOConfig {
  val FIFODepth: Int = 2
}

class FetchFIFO() extends Module with FetchFIFOConfig {
  val io = IO(new Bundle {
    val flush = Input(Bool())
    val in    = Flipped(Decoupled(Input(new FERawInfo)))
    val out   = Decoupled(Vec(2, new FEInfo))
  })

  val entry_vec = RegInit(VecInit(Seq.fill(FIFODepth)(0.U.asTypeOf(new FERawInfo))))
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
      entry_vec(i) := 0.U.asTypeOf(new FERawInfo)
    }

  }.elsewhen(push) {
    entry_vec(wr_ptr_q).pc   := io.in.bits.pc
    entry_vec(wr_ptr_q).inst := io.in.bits.inst
    entry_vec(wr_ptr_q).pred := io.in.bits.pred
    wr_ptr_q                 := wr_ptr_q + 1.U
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
  io.out.bits(0).pc    := Cat(rd_entry.pc(31, 3), 0.U(3.W))
  io.out.bits(0).inst  := rd_entry.inst(31, 0)
  io.out.bits(0).dummy := false.B

  io.out.bits(1).pc    := Cat(rd_entry.pc(31, 3), "b100".U(3.W))
  io.out.bits(1).inst  := rd_entry.inst(63, 32)
  io.out.bits(1).dummy := rd_entry.pred(0)
}
