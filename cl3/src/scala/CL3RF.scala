package cl3

import chisel3._
import chisel3.util._

class RFRdPort extends Bundle {
  val raddr = Input(UInt(5.W))
  val rdata = Output(UInt(32.W))
}

class RFWrPort extends Bundle {
  val wen   = Input(Bool())
  val wdata = Input(UInt(32.W))
  val waddr = Input(UInt(5.W))
}

class CL3RFIO() extends Bundle {
  val rd = Vec(4, new RFRdPort)
  val wr = Vec(2, new RFWrPort)
}

class CL3RF extends Module {
  val io = IO(new CL3RFIO)

  val regs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))

  when(io.wr(1).wen && (io.wr(1).waddr =/= 0.U)) {
    regs(io.wr(1).waddr) := io.wr(1).wdata
  }.elsewhen(io.wr(0).wen && (io.wr(0).waddr =/= 0.U)) {
    regs(io.wr(0).waddr) := io.wr(0).wdata
  }

  for (i <- 0 until 4) {
    io.rd(i).rdata := Mux(io.rd(i).raddr === 0.U, 0.U(32.W), regs(io.rd(i).raddr))

  }

}
