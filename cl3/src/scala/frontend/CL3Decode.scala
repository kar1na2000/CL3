package cl3

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils


class DEIO extends Bundle {
  val br  = Input(new BrInfo)
  val in  = Flipped(Decoupled(Input(new FEInfo)))
  val out = Decoupled(Vec(2, (Output(new DEInfo))))
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
    decoder(i).io.inst   := fifo.io.out.bits(i).inst
    decoder(i).io.pc     := fifo.io.out.bits(i).pc

    io.out.valid         := fifo.io.out.valid
    io.out.bits(i)       := decoder(i).io.out
    fifo.io.out.ready    := io.in.ready

  }

}
