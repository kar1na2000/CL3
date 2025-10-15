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
  val e2    = new PipeE2Output
  val wb    = new PipeWBOutput
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

  val e1_flush = io.in.flush || io.out.flush
  val e1_stall = io.in.stall

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

  class E2Data extends Bundle {
    val valid  = Bool()
    val info   = new DEInfo
    val npc    = UInt(32.W)
    val ra     = UInt(32.W)
    val rb     = UInt(32.W)
    val result = UInt(32.W)
    val except = UInt(6.W)
    val csr    = new Bundle {
      val waddr = UInt(12.W)
      val wdata = UInt(32.W)
      val wen   = Bool()
    }
  }

  val e2_q = RegInit(0.U.asTypeOf(new E2Data))

  val e2_flush = io.in.flush || io.out.flush

  when(e2_flush && !io.in.stall) {
    e2_q.valid := false.B
  }.elsewhen(!io.in.stall) {
    e2_q.valid := e1_q.valid
    e2_q.info  := e1_q.info
    e2_q.npc   := e1_q.npc
    e2_q.ra    := e1_q.ra
    e2_q.rb    := e2_q.rb

    e2_q.result := MuxCase(
      io.in.exu.result,
      Seq(
        e1_q.info.isDIV -> io.in.div.result,
        e1_q.info.isCSR -> io.in.csr.rdata
      )
    )
  }

  io.out.e2.valid := e2_q.valid
  io.out.e2.isLd  := e2_q.info.isLSU && e2_q.info.wen
  io.out.e2.isMUL := e2_q.info.isMUL
  io.out.e2.pc    := e2_q.info.pc
  io.out.e2.inst  := e2_q.info.inst

  io.out.e2.wen := !(io.in.stall || io.out.stall) && e2_q.info.wen

  io.out.e2.result := MuxCase(
    e2_q.result,
    Seq(
      io.out.e2.isLd  -> io.in.lsu.rdata,
      io.out.e2.isMUL -> io.in.mul.result
    )
  )

  io.out.stall := e1_q.valid && e1_q.info.isDIV && !io.in.div.valid ||
    e2_q.valid && io.out.e2.isLd && !io.in.lsu.valid

  class WBData extends Bundle {
    val valid  = Bool()
    val info   = new DEInfo
    val npc    = UInt(32.W)
    val ra     = UInt(32.W)
    val rb     = UInt(32.W)
    val except = UInt(6.W)
    val result = UInt(32.W)
    val wen    = Bool()
    val csr    = new Bundle {
      val waddr = UInt(12.W)
      val wdata = UInt(32.W)
      val wen   = Bool()
    }

    def rdIdx: UInt = info.inst(11, 7)
  }

  io.out.flush := false.B // TODO: add exception

  val wb_q = RegInit(0.U.asTypeOf(new WBData))

  when(!io.in.stall) {
    wb_q.info      := e2_q.info
    wb_q.npc       := e2_q.npc
    wb_q.ra        := e2_q.ra
    wb_q.rb        := e2_q.rb
    wb_q.except    := 0.U // TODO: add exception
    wb_q.csr.wdata := e2_q.csr.wdata
    wb_q.csr.wen   := e2_q.csr.wen
    wb_q.wen       := io.out.e2.wen
    wb_q.result    := io.out.e2.result
  }

  wb_q.valid := e2_q.valid && !io.in.stall && !io.in.flushWB

  io.out.wb.commit := wb_q.valid

  io.out.wb.csr    := wb_q.csr
  io.out.wb.except := wb_q.except
  io.out.wb.inst   := wb_q.info.inst
  io.out.wb.pc     := wb_q.info.pc
  io.out.wb.npc    := wb_q.npc
  io.out.wb.ra     := wb_q.ra
  io.out.wb.rb     := wb_q.rb
  io.out.wb.result := wb_q.result
  io.out.wb.wen    := wb_q.wen

}
