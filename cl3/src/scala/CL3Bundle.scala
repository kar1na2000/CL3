package cl3

import chisel3._
import chisel3.util._

// ======== Branch Bundle ========

class BrUpdateInfo extends Bundle {
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

// ======== Branch Prediction Bundle ========

class NPCIO extends Bundle {
  val pc     = Input(UInt(32.W))
  val accept = Input(Bool())
  val npc    = Output(UInt(32.W))
  val taken  = Output(Bool())
}

class NPCFullIO extends NPCIO {
  val br    = Input(new BrUpdateInfo)
  val flush = Input(Bool())
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
  val uop   = new MicroOp
  val rdIdx = UInt(5.W)
  val raIdx = UInt(5.W)
  val rbIdx = UInt(5.W)
  val ra    = UInt(32.W)
  val rb    = UInt(32.W)
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


class CSRWBInfo extends Bundle {
  val wen        = Bool()
  val waddr      = UInt(12.W)
  val wdata      = UInt(32.W)
  val except     = UInt(6.W)
  val exceptPC   = UInt(32.W)
  val exceptAddr = UInt(32.W)
}

// ======== Pipe IO Bundle ========

class PipeISInput extends Bundle {
  val fire   = Input(Bool())
  val info   = Input(new DEInfo)
  val ra     = Input(UInt(32.W))
  val rb     = Input(UInt(32.W))
  val except = Input(UInt(6.W))
  val taken  = Input(Bool())
  val target = Input(UInt(32.W))
}

class PipeLSUInput extends Bundle {
  val complete = Input(Bool())
  val rdata    = Input(UInt(32.W))
  val except   = Input(UInt(6.W))
}

class PipeCSRInput extends Bundle {
  val rdata  = Input(UInt(32.W))
  val wen    = Input(Bool())
  val wdata  = Input(UInt(32.W))
  val except = Input(UInt(6.W))
}

class PipeDIVInput extends Bundle {
  val complete = Input(Bool())
  val result   = Input(UInt(32.W))
}

class PipeMULInput extends Bundle {
  val result = Input(UInt(32.W))
}

class PipeEXUInput extends Bundle {
  val result = Input(UInt(32.W))
}

class PipeE1Output extends Bundle {
  val valid   = Output(Bool())
  val isLoad  = Output(Bool())
  val isStore = Output(Bool())
  val isMUL   = Output(Bool())
  val isBr    = Output(Bool())
  val rdIdx   = Output(UInt(5.W))
  val pc      = Output(UInt(32.W))
  val inst    = Output(UInt(32.W))
  val ra      = Output(UInt(32.W))
  val rb      = Output(UInt(32.W))
}

class PipeE2Output extends Bundle {
  val valid  = Output(Bool())
  val isLoad = Output(Bool())
  val isMUL  = Output(Bool())
  val result = Output(UInt(32.W))
  val rdIdx  = Output(UInt(5.W))
}

class PipeWBOutput extends Bundle {
  val commit = Output(Bool())
  val pc     = Output(UInt(32.W))
  val inst   = Output(UInt(32.W))
  val except = Output(UInt(6.W))
  val ra     = Output(UInt(32.W))
  val rb     = Output(UInt(32.W))
  val result = Output(UInt(32.W))
  val rdIdx  = Output(UInt(5.W))

  val csr = new Bundle {
    val wen   = Output(Bool())
    val waddr = Output(UInt(12.W))
    val wdata = Output(UInt(32.W))
  }
}
