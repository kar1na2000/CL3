package cl3

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import CL3InstInfo._

class CSRInput extends Bundle {
  val info = Input(new OpInfo)
  val wb   = Flipped(new ISCSROutput)
  val irq  = Input(Bool())

  val bootAddr = Input(UInt(32.W))
}

class CSROutput extends Bundle {
  val info   = Flipped(new ISCSRInput)
  val irq    = Output(Bool())
  val ifence = Output(Bool()) // TODO:
  val mmu    = Output(new MMUCtrlInfo)
}

class CSRIO extends Bundle {
  val in  = new CSRInput
  val out = new CSROutput
}

class CL3CSR extends Module with CSRConstant {

  val io = IO(new CSRIO)

  val table = new DecodeTable(instPatterns, Seq(CSROPField))
  val res   = table.decode(io.in.info.inst)
  val op    = res(CSROPField)

  val set   = isSet(op)
  val clear = isClear(op)

  val raddr = io.in.info.inst(31, 20)

  val csr_rf = Module(new CL3CSRRF())

  csr_rf.io.raddr    := raddr
  csr_rf.io.ren      := true.B // TODO:
  csr_rf.io.bootAddr := io.in.bootAddr

  csr_rf.io.waddr := io.in.wb.waddr
  csr_rf.io.wdata := io.in.wb.wdata
  csr_rf.io.wen   := io.in.wb.wen
  csr_rf.io.trap  := 0.U // TODO:
  csr_rf.io.pc    := io.in.info.pc
  csr_rf.io.inst  := io.in.info.inst

  io.out.ifence  := false.B
  io.out.irq     := false.B
  io.out.mmu     := 0.U.asTypeOf(new MMUCtrlInfo)
  io.out.info.br := csr_rf.io.br

  io.out.info.rdata  := csr_rf.io.rdata
  io.out.info.except := 0.U
  io.out.info.wdata  := 0.U
  io.out.info.wen    := false.B

}

object CSROPField extends DecodeField[InstructionPattern, UInt] {
  def name: String = "CSR OP"

  import CSRConstant._
  def chiselType: UInt = UInt(CSR_OP_WIDTH.W)

  def genTable(op: InstructionPattern): BitPat = {

    op.name match {
      case "csrrw"  => BitPat(OP_CSRRW)
      case "csrrs"  => BitPat(OP_CSRRS)
      case "csrrc"  => BitPat(OP_CSRRC)
      case "csrrwi" => BitPat(OP_CSRRWI)
      case "csrrsi" => BitPat(OP_CSRRSI)
      case "csrrci" => BitPat(OP_CSRRCI)
      case "fence"  => BitPat(OP_FENCE)
      case _        => BitPat.dontCare(CSR_OP_WIDTH)
    }
  }
}
