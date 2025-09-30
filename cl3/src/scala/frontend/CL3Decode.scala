package cl3

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

trait FetchFIFOConfig {
  val FIFOWidth:   Int = 64
  val FIFODepth:   Int = 2
  val opInfoWidth: Int = 10
}

class FetchFIFO() extends Module with FetchFIFOConfig {
  val io = IO(new Bundle {
    val flush = Input(Bool())
    val in    = Flipped(Decoupled(new FetchFIFOInput))
    val out   = Vec(2, Decoupled(new FetchFIFOOutput))
  })

  class FIFOEntry() extends Bundle {
    val pc     = UInt(32.W)
    val inst   = UInt(FIFOWidth.W)
    val info0  = UInt(opInfoWidth.W)
    val info1  = UInt(opInfoWidth.W)
    val valid0 = Bool()
    val valid1 = Bool()
  }

  val entry_vec = RegInit(VecInit(Seq.fill(FIFODepth)(0.U.asTypeOf(new FIFOEntry))))
  val rd_ptr_q  = RegInit(0.U(1.W))
  val wr_ptr_q  = RegInit(0.U(1.W))
  val count_q   = RegInit(0.U(2.W))

  val is_full  = (count_q === FIFODepth.U)
  val is_empty = (count_q === 0.U)

  io.in.ready := !is_full

  val head = entry_vec(rd_ptr_q)

  io.out(0).valid := !is_empty && head.valid0
  io.out(1).valid := !is_empty && head.valid1

  val push = io.in.fire
  val pop0 = io.out(0).fire
  val pop1 = io.out(1).fire

  val pop_complete = !is_empty && ((pop0 && !head.valid1) || (pop1 && !head.valid0) || (pop0 && pop1))

  when(push) {
    entry_vec(wr_ptr_q).pc     := io.in.bits.pc
    entry_vec(wr_ptr_q).inst   := io.in.bits.data
    entry_vec(wr_ptr_q).info0  := io.in.bits.info0
    entry_vec(wr_ptr_q).info1  := io.in.bits.info1
    entry_vec(wr_ptr_q).valid0 := true.B

    // If the previous instruction was a speculatively taken branch, invalidate the current instruction
    entry_vec(wr_ptr_q).valid1 := !io.in.bits.info0(2)
    wr_ptr_q                   := wr_ptr_q + 1.U
  }

  when(pop0) {
    entry_vec(rd_ptr_q).valid0 := false.B
  }
  when(pop1) {
    entry_vec(rd_ptr_q).valid1 := false.B
  }

  when(pop_complete) {
    rd_ptr_q := rd_ptr_q + 1.U
  }

  when(push && !pop_complete) {
    count_q := count_q + 1.U
  }.elsewhen(!push && pop_complete) {
    count_q := count_q - 1.U
  }

  val rd_entry = entry_vec(rd_ptr_q)

  // TODO: We should change PC logic when we support C extension
  io.out(0).bits.pc   := Cat(rd_entry.pc(31, 3), 0.U(3.W))
  io.out(0).bits.inst := rd_entry.inst((FIFOWidth / 2) - 1, 0)
  io.out(0).bits.info := rd_entry.info0

  io.out(1).bits.pc   := Cat(rd_entry.pc(31, 3), "b100".U(3.W))
  io.out(1).bits.inst := rd_entry.inst(FIFOWidth - 1, FIFOWidth / 2)
  io.out(1).bits.info := rd_entry.info1
}

class DEIO extends Bundle {
  val br  = Input(new BrInfo)
  val in  = Flipped(Decoupled(Input(new FEInfo)))
  val out = Vec(2, Decoupled(Output(new DEInfo)))
}

class CL3Decode extends Module {

  val io = IO(new DEIO)

  val fifo = Module(new FetchFIFO)

  val info = Cat(io.in.bits.pred, io.in.bits.fault)

  fifo.io.flush := io.br.valid

  fifo.io.in.valid      := io.in.valid
  fifo.io.in.bits.data  := io.in.bits.inst
  fifo.io.in.bits.pc    := io.in.bits.pc
  fifo.io.in.bits.info0 := info
  fifo.io.in.bits.info1 := info
  io.in.ready           := fifo.io.in.ready

  val decoder = Seq.fill(2)(Module(new CL3Decoder))

  for (i <- 0 until 2) {
    decoder(i).io.inst := fifo.io.out(i).bits.inst
    decoder(i).io.pc   := fifo.io.out(i).bits.pc

    io.out(i).bits       := decoder(i).io.out
    io.out(i).valid      := fifo.io.out(i).valid
    fifo.io.out(i).ready := io.out(i).ready

  }

}
