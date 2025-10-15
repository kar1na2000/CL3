package cl3

import chisel3._
import chisel3.util._
import chisel3.experimental.dataview._

// ========== Branch Prediction Bundle ========== //

class BpInfo extends Bundle {
  val valid      = Bool()
  val target     = UInt(32.W)
  val isTaken    = Bool()
  val isNotTaken = Bool()
  val source     = UInt(32.W)
  val isCall     = Bool()
  val isRet      = Bool()
  val isJmp      = Bool()
}

class BrInfo extends Bundle {
  val valid = Bool()
  val pc    = UInt(32.W)
  val priv  = UInt(2.W)
}

// ===================  NPC Bundle =================== //

class NPCIO extends Bundle {
  val pc     = Input(UInt(32.W))
  val accept = Input(Bool())
  val npc    = Output(UInt(32.W))
  val taken  = Output(Bool())
}

class NPCFullIO extends Bundle {
  val bp    = Input(new BpInfo)
  val flush = Input(Bool())
  val info  = new NPCIO
}

class FEInfo extends Bundle {
  val pc    = UInt(32.W)
  val inst  = UInt(64.W)
  val pred  = UInt(2.W)
  val fault = UInt(2.W)
}

class MicroOp extends Bundle {
  val op0 = UInt(4.W)
  val op1 = UInt(3.W)
  val op2 = UInt(3.W)
}

class OpInfo extends Bundle {
  val valid = Bool()
  val inst  = UInt(32.W)
  val pc    = UInt(32.W)
  val wen   = Bool()
  val uop   = new MicroOp
  val ra    = UInt(32.W)
  val rb    = UInt(32.W)

  def rdIdx: UInt = inst(11, 7)

  def raIdx: UInt = inst(19, 15)

  def rbIdx: UInt = inst(24, 20)

}

object OpInfo {
  def fromDE(de: DEInfo): OpInfo = {
    val op = Wire(new OpInfo)
    op.inst  := de.inst
    op.pc    := de.pc
    op.uop   := de.uop
    op.wen   := de.wen
    op.ra    := 0.U     // will be overwrite
    op.rb    := 0.U     // will be overwrite
    op.valid := false.B // will be overwrite

    op
  }
}

class DEInfo extends Bundle {
  val inst    = UInt(32.W)
  val pc      = UInt(32.W)
  val wen     = Bool()
  val isLSU   = Bool()
  val isMUL   = Bool()
  val isDIV   = Bool()
  val isCSR   = Bool()
  val isEXU   = Bool()
  val isBr    = Bool()
  val uop     = new MicroOp
  val illegal = Bool()

  def rdIdx: UInt = inst(11, 7)
  def raIdx: UInt = inst(19, 15)
  def rbIdx: UInt = inst(24, 20)
}

class MMUCtrlInfo extends Bundle {
  val priv  = UInt(2.W)
  val sum   = Bool()
  val mxr   = Bool()
  val flush = Bool()
  val satp  = UInt(32.W)
}

class FetchFIFOInput extends Bundle with FetchFIFOConfig {
  val pc    = Input(UInt(32.W))
  val data  = Input(UInt(FIFOWidth.W))
  val info0 = Input(UInt(opInfoWidth.W))
  val info1 = Input(UInt(opInfoWidth.W))
}

class FetchFIFOOutput extends Bundle with FetchFIFOConfig {
  val pc   = Output(UInt(32.W))
  val inst = Output(UInt((FIFOWidth / 2).W))
  val info = Output(UInt(opInfoWidth.W))
}

// ==================== Pipe Bundle =================== //

class PipeISInput extends Bundle {
  val fire   = Input(Bool())
  val info   = Input(new DEInfo)
  val ra     = Input(UInt(32.W))
  val rb     = Input(UInt(32.W))
  val except = Input(UInt(6.W))

  def rdIdx: UInt = info.inst(11, 7)
}

class PipeLSUInput extends Bundle {
  val valid  = Input(Bool())
  val rdata  = Input(UInt(32.W))
  val except = Input(UInt(6.W))
  val stall  = Input(Bool())
}

class PipeCSRInput extends Bundle {
  val wen    = Input(Bool())
  val rdata  = Input(UInt(32.W))
  val wdata  = Input(UInt(32.W))
  val except = Input(UInt(6.W))
}

class PipeDIVInput extends Bundle {
  val valid  = Input(Bool())
  val result = Input(UInt(32.W))
}

class PipeMULInput extends Bundle {
  val result = Input(UInt(32.W))
}

class PipeEXUInput extends Bundle {
  val result = Input(UInt(32.W))
  val br     = Input(new BrInfo)
}

class PipeInfo extends Bundle {
  val valid  = Bool()
  val info   = new DEInfo
  val pc     = UInt(32.W)
  val npc    = UInt(32.W)
  val ra     = UInt(32.W)
  val rb     = UInt(32.W)
  val except = UInt(6.W)
  val result = UInt(32.W)

  def rdIdx: UInt = info.inst(11, 7)

  def isMul: Bool = valid && info.isMUL

  def isLd: Bool = valid && info.isLSU && info.wen

  def isSt: Bool = valid && info.isLSU && !info.wen

  def isBr: Bool = valid && info.isBr && !info.wen

  def isJmp: Bool = valid && info.isBr && info.wen

  def isALU: Bool = valid && info.isEXU && !info.isBr

  def isMem: Bool = isLd || isSt

  def hazard_detect(rsIdx: UInt): Bool = {
    rsIdx === rdIdx && (isLd || isMul)
  }

}

class PipeE1Output extends Bundle {
  val valid  = Output(Bool())
  val isLd   = Output(Bool())
  val isSt   = Output(Bool())
  val isMUL  = Output(Bool())
  val isBr   = Output(Bool())
  val isEXU  = Output(Bool())
  val pc     = Output(UInt(32.W))
  val inst   = Output(UInt(32.W))
  val ra     = Output(UInt(32.W))
  val rb     = Output(UInt(32.W))
  val result = Output(UInt(32.W))
  val wen    = Output(Bool())

  def rdIdx: UInt = inst(11, 7)
  def isLSU: Bool = valid && (isLd || isSt)
}

class PipeE2Output extends Bundle {
  val valid  = Output(Bool())
  val isLd   = Output(Bool())
  val isMUL  = Output(Bool())
  val pc     = Output(UInt(32.W))
  val inst   = Output(UInt(32.W))
  val wen    = Output(Bool())
  val result = Output(UInt(32.W))

  def rdIdx: UInt = inst(11, 7)
}

class PipeWBOutput extends Bundle {
  val commit = Output(Bool())
  val pc     = Output(UInt(32.W))
  val npc    = Output(UInt(32.W))
  val inst   = Output(UInt(32.W))
  val except = Output(UInt(6.W))
  val ra     = Output(UInt(32.W))
  val rb     = Output(UInt(32.W))
  val result = Output(UInt(32.W))
  val wen    = Output(Bool())

  val csr = new Bundle {
    val wen   = Output(Bool())
    val waddr = Output(UInt(12.W))
    val wdata = Output(UInt(32.W))
  }

  def rdIdx: UInt = inst(11, 7)
}

class ISCSRInput extends Bundle {
  val br     = Input(new BrInfo)
  val wen    = Input(Bool())
  val rdata  = Input(UInt(32.W))
  val wdata  = Input(UInt(32.W))
  val except = Input(UInt(6.W))
}

class ISEXUInput extends Bundle {
  val br     = Input(new BrInfo)
  val bp     = Input(new BpInfo)
  val result = Input(UInt(32.W))
}

class ISCSROutput extends Bundle {
  val wen    = Output(Bool())
  val waddr  = Output(UInt(12.W))
  val wdata  = Output(UInt(32.W))
  val except = Output(UInt(6.W))
}

class BypassISInfo extends Bundle {
  val raIdx = UInt(5.W)
  val rbIdx = UInt(5.W)
  val ra    = UInt(32.W)
  val rb    = UInt(32.W)
}
