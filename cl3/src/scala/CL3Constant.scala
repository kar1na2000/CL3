package cl3

import chisel3._
import chisel3.util._

trait MMUConstant {

//TODO:
  val CacheableAddrMin = "h00000000".U
  val CacheableAddrMax = "h80000000".U

  val PAGE_PRESENT  = 0
  val PAGE_READ     = 1
  val PAGE_WRITE    = 2
  val PAGE_EXEC     = 3
  val PAGE_USER     = 4
  val PAGE_GLOBAL   = 5
  val PAGE_ACCESSED = 6
  val PAGE_DIRTY    = 7
}

object MMUConstant extends MMUConstant {}

trait CSRConstant {

  val MHARTID_ADDR   = 0xf14.U(12.W)
  val MSTATUS_ADDR   = 0x300.U(12.W)
  val MTVEC_ADDR     = 0x305.U(12.W)
  val MSCRATCH_ADDR  = 0x340.U(12.W)
  val MEPC_ADDR      = 0x341.U(12.W)
  val MCAUSE_ADDR    = 0x342.U(12.W)
  val DCSR_ADDR      = 0x7b0.U(12.W)
  val DPC_ADDR       = 0x7b1.U(12.W)
  val DSCRATCH0_ADDR = 0x7b2.U(12.W)
  val DSCRATCH1_ADDR = 0x7b3.U(12.W)

}

object CSRConstant extends CSRConstant {}

//TODO: use a more elegant way
trait OpConstant {

  val OP0_WIDTH = 4
  val OP1_WIDTH = 3
  val OP2_WIDTH = 3

  val OP0_ADD  = "b0001".U(OP0_WIDTH.W)
  val OP0_SUB  = "b0101".U(OP0_WIDTH.W)
  val OP0_AND  = "b0010".U(OP0_WIDTH.W)
  val OP0_OR   = "b0011".U(OP0_WIDTH.W)
  val OP0_XOR  = "b0100".U(OP0_WIDTH.W)
  val OP0_SLT  = "b0111".U(OP0_WIDTH.W)
  val OP0_SLTU = "b0110".U(OP0_WIDTH.W)
  val OP0_SLL  = "b1000".U(OP0_WIDTH.W)
  val OP0_SRL  = "b1100".U(OP0_WIDTH.W)
  val OP0_SRA  = "b1110".U(OP0_WIDTH.W)
  val OP0_NONE = "b0000".U(OP0_WIDTH.W)

  val OP1_REG      = "b000".U(OP1_WIDTH.W)
  val OP1_PC       = "b111".U(OP1_WIDTH.W)
  val OP1_BEQ      = "b001".U(OP1_WIDTH.W)
  val OP1_BGE      = "b010".U(OP1_WIDTH.W)
  val OP1_BGEU     = "b100".U(OP1_WIDTH.W)
  val OP1_BNE      = "b110".U(OP1_WIDTH.W)
  val OP1_BLT      = "b101".U(OP1_WIDTH.W)
  val OP1_BLTU     = "b011".U(OP1_WIDTH.W)

  val OP2_REG  = "b001".U(OP2_WIDTH.W)
  val OP2_NONE = "b000".U(OP2_WIDTH.W)
  val OP2_IMMU = "b100".U(OP2_WIDTH.W)
  val OP2_IMMI = "b101".U(OP2_WIDTH.W)
  val OP2_IMMB = "b110".U(OP2_WIDTH.W)
  val OP2_IMMJ = "b111".U(OP2_WIDTH.W)

}

object OpConstant extends OpConstant {}
