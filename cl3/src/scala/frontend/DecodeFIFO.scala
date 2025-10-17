package cl3

import chisel3._
import chisel3.util._

trait DecodeFIFOConfig {
  val FIFOWidth: Int = 64
  val FIFODepth: Int = 2
}

class DecodeFIFO() extends Module with DecodeFIFOConfig {
  val io = IO(new Bundle {
    val flush = Input(Bool())
    val in    = Flipped(Decoupled(Vec(2, Input(new DEInfo))))
    val out   = Vec(2, Decoupled(Output(new DEInfo)))
  })

  class FIFOEntry() extends Bundle {
    val info0  = new DEInfo
    val info1  = new DEInfo
    val valid0 = Bool()
    val valid1 = Bool()
  }

  val entry_vec = RegInit(VecInit(Seq.fill(FIFODepth)(0.U.asTypeOf(new FIFOEntry))))
  val rd_ptr_q  = RegInit(0.U(1.W))
  val wr_ptr_q  = RegInit(0.U(1.W))
  val count_q   = RegInit(0.U(2.W))

  val is_full  = (count_q === FIFODepth.U)
  val is_empty = (count_q === 0.U)

  io.in.ready := !is_full && !io.flush

  val head = entry_vec(rd_ptr_q)

  io.out(0).valid := !is_empty && head.valid0
  io.out(1).valid := !is_empty && head.valid1

  val push = io.in.fire
  val pop0 = io.out(0).fire
  val pop1 = io.out(1).fire

  // val pop_complete = !is_empty && ((pop0 && !head.valid1) || (pop1 && !head.valid0) || (pop0 && pop1))
  val pop_complete = !is_empty && (pop1 && head.valid0 && head.valid1) || (pop0 && head.valid0 && !head.valid1) || (pop1 && !head.valid0 && head.valid1)

  when(io.flush) {
    wr_ptr_q := 0.U
    for (i <- 0 until FIFODepth) {
      entry_vec(i) := 0.U.asTypeOf(new FIFOEntry)
    }

  }.elsewhen(push) {
    entry_vec(wr_ptr_q).info0  := io.in.bits(0)
    entry_vec(wr_ptr_q).info1  := io.in.bits(1)
    entry_vec(wr_ptr_q).valid0 := !io.in.bits(0).dummy
    entry_vec(wr_ptr_q).valid1 := !io.in.bits(1).dummy

    wr_ptr_q := wr_ptr_q + 1.U
  }

  when(pop0) {
    entry_vec(rd_ptr_q).valid0 := false.B
  }
  when(pop1) {
    entry_vec(rd_ptr_q).valid1 := false.B
  }

  when(io.flush) {
    rd_ptr_q := 0.U
  }.elsewhen(pop_complete) {
    rd_ptr_q := rd_ptr_q + 1.U
  }

  when(io.flush) {
    count_q := 0.U
  }.elsewhen(push && !pop_complete) {
    count_q := count_q + 1.U
  }.elsewhen(!push && pop_complete) {
    count_q := count_q - 1.U
  }

  val rd_entry = entry_vec(rd_ptr_q)

  io.out(0).bits := rd_entry.info0
  io.out(1).bits := rd_entry.info1

}
