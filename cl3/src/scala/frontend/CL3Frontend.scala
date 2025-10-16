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
  npc.io.bp    := io.bp
  npc.io.flush := false.B

  val decode = Module(new CL3Decode)
  decode.io.br := io.br

  val fetch = Module(new CL3Fetch)
  fetch.io.mem <> io.mem
  fetch.io.bp <> npc.io.info
  fetch.io.de <> decode.io.in
  fetch.io.br    := io.br
  fetch.io.flush := false.B

  val fifo = Module(new DecodeFIFO)
  fifo.io.flush := io.br.valid
  fifo.io.in <> decode.io.out
  io.out <> fifo.io.out

}
