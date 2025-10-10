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

  val CSR_OP_WIDTH = 5

  // TODO: check the opcode
  val OP_ECALL  = "b00001".U(CSR_OP_WIDTH.W)
  val OP_EBREAK = "b00010".U(CSR_OP_WIDTH.W)
  val OP_ERET   = "b00100".U(CSR_OP_WIDTH.W)

  val OP_CSRRW  = "b01001".U(CSR_OP_WIDTH.W)
  val OP_CSRRS  = "b01010".U(CSR_OP_WIDTH.W)
  val OP_CSRRC  = "b01000".U(CSR_OP_WIDTH.W)
  val OP_CSRRWI = "b01101".U(CSR_OP_WIDTH.W)
  val OP_CSRRSI = "b01110".U(CSR_OP_WIDTH.W)
  val OP_CSRRCI = "b01100".U(CSR_OP_WIDTH.W)

  val OP_FENCE  = "b10001".U(CSR_OP_WIDTH.W)
  val OP_SFENCE = "b10010".U(CSR_OP_WIDTH.W)
  val OP_IFENCE = "b10100".U(CSR_OP_WIDTH.W)

  val OP_WFI = "b11111".U(CSR_OP_WIDTH.W)

  def isSet(op: UInt): Bool = op(4, 3) === "b01".U && op(0) && op(1)

  def isClear(op: UInt): Bool = op(4, 3) === "b01".U && !op(1)
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

  val OP1_Z   = "b000".U(OP1_WIDTH.W)
  val OP1_PC  = "b111".U(OP1_WIDTH.W)
  val OP1_REG = "b001".U(OP1_WIDTH.W)
  val OP1_BEQ = "b010".U(OP1_WIDTH.W)
  val OP1_BGE = "b011".U(OP1_WIDTH.W)
  val OP1_BNE = "b100".U(OP1_WIDTH.W)
  val OP1_BLT = "b101".U(OP1_WIDTH.W)

  val OP2_REG  = "b001".U(OP2_WIDTH.W)
  val OP2_NONE = "b000".U(OP2_WIDTH.W)
  val OP2_IMMU = "b100".U(OP2_WIDTH.W)
  val OP2_IMMI = "b101".U(OP2_WIDTH.W)
  val OP2_IMMB = "b110".U(OP2_WIDTH.W)
  val OP2_IMMJ = "b111".U(OP2_WIDTH.W)

}

object OpConstant extends OpConstant {}

trait LSUConstant {

  val LSU_WIDTH = 4

  val LSU_LS_BIT   = LSU_WIDTH - 1
  val LSU_SIGN_BIT = 0
  val LSU_1B       = 1
  val LSU_2B       = 2
  val LSU_4B       = 3

// |---L/S---|---Size(2 bit)---|---sign---|
// signal[3]: 0 -> Load, 1 -> Store
// signal[2:1]: 0 -> invalid, 1 -> 1 byte, 2 -> 2 bytes, 3 -> 4 bytes
// signal[0]: 0 -> signed, 1 -> unsigned

//TODO: support xlen = 64
  val LSU_XXX = "b0000".U(LSU_WIDTH.W)
  val LSU_SB  = "b1010".U(LSU_WIDTH.W)
  val LSU_SH  = "b1100".U(LSU_WIDTH.W)
  val LSU_SW  = "b1110".U(LSU_WIDTH.W)
  val LSU_LB  = "b0010".U(LSU_WIDTH.W)
  val LSU_LBU = "b0011".U(LSU_WIDTH.W)
  val LSU_LH  = "b0100".U(LSU_WIDTH.W)
  val LSU_LHU = "b0101".U(LSU_WIDTH.W)
  val LSU_LW  = "b0110".U(LSU_WIDTH.W)

  val MASK_Z   = "b0000".U(4.W)
  val MASK_ALL = "b1111".U(4.W)
  val MASK_HI  = "b1100".U(4.W)
  val MASK_LO  = "b0011".U(4.W)
  val MASK_B0  = "b0001".U(4.W)
  val MASK_B1  = "b0010".U(4.W)
  val MASK_B2  = "b0100".U(4.W)
  val MASK_B3  = "b1000".U(4.W)
}

object LSUConstant extends LSUConstant {}

trait AXIConstant {

  val AXI_FIXED = 0.U
  val AXI_INCR  = 1.U
  val AXI_WRAP  = 2.U

}

object AXIConstant extends AXIConstant {}
