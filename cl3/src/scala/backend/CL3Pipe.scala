package cl3

import chisel3._
import chisel3.util._

class PipeInput extends Bundle {

  val issue = new PipeISInput
  val lsu   = new PipeLSUInput
  val exu   = new PipeEXUInput
  val div   = new PipeDIVInput
  val mul   = new PipeMULInput
  val csr   = new PipeCSRInput

  val irq     = Input(Bool())
  val flush   = Input(Bool())
  val flushWB = Input(Bool())
  val stall   = Input(Bool())

}

class PipeOutput extends Bundle {
  val e1    = Output(new PipeInfo)
  val e2    = Output(new PipeInfo)
  val wb    = Output(new PipeInfo)
  val stall = Output(Bool())
  val flush = Output(Bool())
}

class PipeIO extends Bundle {
  val in  = new PipeInput
  val out = new PipeOutput
}

class CL3Pipe() extends Module {

  val io = IO(new PipeIO)

  val e1_q = RegInit(0.U.asTypeOf(new PipeInfo))

  val e1_flush = io.in.flush || io.out.flush //TODO:
  val e1_stall = io.in.stall //TODO:

  when(e1_flush && !e1_stall) {
    e1_q.valid := false.B
  }.elsewhen(!e1_stall) {
    e1_q.valid := io.in.issue.fire
  }

  when(io.in.issue.fire && !e1_flush && !e1_stall) {
    e1_q.info   := io.in.issue.info
    // TODO: support RVC
    e1_q.npc    := Mux(io.in.exu.br.valid, io.in.exu.br.pc, io.in.issue.info.pc + 4.U)
    e1_q.ra     := io.in.issue.ra
    e1_q.rb     := io.in.issue.rb
    e1_q.except := io.in.issue.except
  }

  io.out.e1        := e1_q
  io.out.e1.result := io.in.exu.result

  val e2_q = RegInit(0.U.asTypeOf(new PipeInfo)) 

  val e2_flush = io.in.flush || io.out.flush //TODO:
  val e2_stall = io.in.stall //TODO:

  when(e2_flush && !io.in.stall) {
    e2_q.valid := false.B
  }.elsewhen(!io.in.stall) {
    e2_q       := e1_q
    e2_q.result := MuxCase(io.in.exu.result, Seq(
      e1_q.info.isDIV -> io.in.div.result,
      e1_q.info.isCSR -> io.in.csr.rdata))
  }
  
  io.out.e2 := e2_q
  io.out.e2.info.wen := !(io.in.stall || io.out.stall) && e2_q.info.wen
  io.out.e2.result := MuxCase(
    e2_q.result,
    Seq(
      io.out.e2.isLd  -> io.in.lsu.rdata,
      io.out.e2.isMul -> io.in.mul.result
    )
  )

  io.out.stall := e1_q.valid && e1_q.info.isDIV && !io.in.div.valid ||
    e2_q.valid && e2_q.info.isLSU && !io.in.lsu.valid
  io.out.flush := false.B // TODO: add exception

  val wb_q   = RegInit(0.U.asTypeOf(new PipeInfo))

  when(!io.in.stall) {
    wb_q := e2_q
    wb_q.result    := io.out.e2.result
    wb_q.mem.cacheable := Mux(e2_q.isMem, io.in.lsu.cacheable, true.B)
  }

  wb_q.valid := e2_q.valid && !io.in.stall && !io.in.flushWB
  io.out.wb := wb_q
}
