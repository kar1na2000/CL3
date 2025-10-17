package cl3

import chisel3._
import chisel3.util._

class FrontendIO extends Bundle {
  val mem = new CL3ICacheIO
  val br  = Input(new BrInfo)
  val bp  = Input(new BpInfo)
  val out = Vec(2, Decoupled(Output(new DEInfo)))
}

class CL3Frontend extends Module {

  val io = IO(new FrontendIO)

  val npc = Module(new CL3NPC)
  npc.io.bp      := io.bp
  npc.io.mispred := io.br.valid

  val fetch = Module(new CL3Fetch)
  fetch.io.mem <> io.mem
  fetch.io.bp <> npc.io.info
  fetch.io.br    := io.br
  fetch.io.flush := false.B

  val fe_fifo = Module(new FetchFIFO)
  fe_fifo.io.flush := io.br.valid
  fe_fifo.io.in <> fetch.io.de

  val decode = Module(new CL3Decode)
  decode.io.in <> fe_fifo.io.out

  val de_fifo = Module(new DecodeFIFO)
  de_fifo.io.flush := io.br.valid
  de_fifo.io.in <> decode.io.out
  io.out <> de_fifo.io.out

}
