package cl3

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import OpConstant._
import chisel3.util.experimental.BoringUtils

// Note that Chisel Decoder API is still an experimental feature
// TODO: add a traditional decoder

case class InstructionPattern(
  val instType: String,
  val opType:   String, // TODO: change to enum
  val name:     String,
  val func7:    BitPat = BitPat.dontCare(7),
  val func3:    BitPat = BitPat.dontCare(3),
  val opcode:   BitPat)
    extends DecodePattern {
  def bitPat: BitPat = pattern

  val pattern = func7 ## BitPat.dontCare(10) ## func3 ## BitPat.dontCare(5) ## opcode

}

//TODO: check timing
object OP0Field extends DecodeField[InstructionPattern, UInt] {
  def name: String = " OP0 "

  def chiselType: UInt = UInt(OP0_WIDTH.W)

  def genTable(op: InstructionPattern): BitPat = {
    val i = op.name match {
      case "slti"  => BitPat(OP0_SLT)
      case "sltiu" => BitPat(OP0_SLTU)
      case "xori"  => BitPat(OP0_XOR)
      case "andi"  => BitPat(OP0_AND)
      case "srai"  => BitPat(OP0_SRA)
      case "srli"  => BitPat(OP0_SRL)
      case "slli"  => BitPat(OP0_SLL)
      case "ori"   => BitPat(OP0_OR)
      case "jalr"  => BitPat(OP0_NONE)
      case _       => BitPat(OP0_ADD)
    }

    val b = op.name match {
      case "bltu" => BitPat(OP0_SLTU)
      case "bgeu" => BitPat(OP0_SLTU)
      case _      => BitPat(OP0_SLT)
    }

    val r = op.name match {

      case "add"  => BitPat(OP0_ADD)
      case "sub"  => BitPat(OP0_SUB)
      case "sll"  => BitPat(OP0_SLL)
      case "slt"  => BitPat(OP0_SLT)
      case "sltu" => BitPat(OP0_SLTU)
      case "xor"  => BitPat(OP0_XOR)
      case "srl"  => BitPat(OP0_SRL)
      case "sra"  => BitPat(OP0_SRA)
      case "or"   => BitPat(OP0_OR)
      case "and"  => BitPat(OP0_AND)
      case _      => BitPat.dontCare(OP0_WIDTH)
    }

    op.instType match {
      case "U" => BitPat(OP0_ADD)
      case "J" => BitPat(OP0_ADD)
      case "I" => i
      case "B" => b
      case "S" => BitPat(OP0_ADD)
      case "R" => r
      case _   => BitPat(OP0_ADD)
    }
  }
}
object OP1Field extends DecodeField[InstructionPattern, UInt] {
  def name: String = " OP1 "

  def chiselType: UInt = UInt(OP1_WIDTH.W)

  def genTable(op: InstructionPattern): BitPat = {

    val b = op.name match {
      case "beq"  => BitPat(OP1_BEQ)
      case "blt"  => BitPat(OP1_BLT)
      case "bltu" => BitPat(OP1_BLT)
      case "bne"  => BitPat(OP1_BNE)
      case "bge"  => BitPat(OP1_BGE)
      case "bgeu" => BitPat(OP1_BGE)
      case _      => BitPat.dontCare(OP1_WIDTH)
    }

    val u = op.name match {
      case "lui"   => BitPat(OP1_Z)
      case "auipc" => BitPat(OP1_PC)
      case _       => BitPat.dontCare(OP1_WIDTH)
    }

    op.instType match {
      case "U" => u
      case "J" => BitPat(OP1_PC)
      case "I" => BitPat(OP1_REG)
      case "B" => b
      case "S" => BitPat(OP1_REG)
      case "R" => BitPat(OP1_REG)
      case _   => BitPat.dontCare(OP1_WIDTH)
    }
  }
}

object OP2Field     extends DecodeField[InstructionPattern, UInt] {
  def name: String = " OP2 "

  def chiselType: UInt = UInt(OP2_WIDTH.W)

  def genTable(op: InstructionPattern): BitPat = {

    op.instType match {
      case "U" => BitPat(OP2_IMMU)
      case "J" => BitPat(OP2_IMMJ)
      case "I" => BitPat(OP2_IMMI)
      case "B" => BitPat(OP2_REG)
      case "S" => BitPat(OP2_REG)
      case "R" => BitPat(OP2_REG)
      case _   => BitPat.dontCare(OP2_WIDTH)
    }
  }
}
object IllegalField extends BoolDecodeField[InstructionPattern]   {
  def name: String = "illegal instruction"

  def genTable(op: InstructionPattern): BitPat = {
    op.instType match {
      case "R"    => BitPat(false.B)
      case "J"    => BitPat(false.B)
      case "I"    => BitPat(false.B)
      case "M"    => BitPat(false.B)
      case "B"    => BitPat(false.B)
      case "S"    => BitPat(false.B)
      case "U"    => BitPat(false.B)
      case "CSR"  => BitPat(false.B)
      case "PRIV" => BitPat(false.B)
      case _      => BitPat(true.B)
    }
  }
}

object WENField extends BoolDecodeField[InstructionPattern] {
  def name: String = "Write back enable"

  def genTable(op: InstructionPattern): BitPat = {
    if (op.instType == "B" || op.instType == "S") {
      BitPat(false.B)
    } else if (op.name == "ecall/ebreak")
      BitPat(false.B)
    else
      BitPat(true.B)
  }
}

object EXUField extends BoolDecodeField[InstructionPattern] {
  def name: String = " EXU instruction"

  def genTable(op: InstructionPattern): BitPat = {
    op.opType match {
      case "EXEC" => BitPat(true.B)
      case _      => BitPat(false.B)
    }
  }
}

object LSUField extends BoolDecodeField[InstructionPattern] {
  def name: String = " LSU instruction"

  def genTable(op: InstructionPattern): BitPat = {
    op.opType match {
      case "MEM" => BitPat(true.B)
      case _     => BitPat(false.B)
    }
  }
}

object MULField extends BoolDecodeField[InstructionPattern] {
  def name: String = " MUL instruction"

  def genTable(op: InstructionPattern): BitPat = {
    op.opType match {
      case "MUL" => BitPat(true.B)
      case _     => BitPat(false.B)
    }
  }
}

object DIVField extends BoolDecodeField[InstructionPattern] {
  def name: String = "DIV instruction"

  def genTable(op: InstructionPattern): BitPat = {
    op.opType match {
      case "DIV" => BitPat(true.B)
      case _     => BitPat(false.B)
    }
  }
}

object CSRField extends BoolDecodeField[InstructionPattern] {
  def name: String = "CSR instruction"

  def genTable(op: InstructionPattern): BitPat = {
    op.opType match {
      case "CSR" => BitPat(true.B)
      case _     => BitPat(false.B)
    }
  }
}

object BRField extends BoolDecodeField[InstructionPattern] {
  def name: String = "Branch instruction"

  def genTable(op: InstructionPattern): BitPat = {
    op.opType match {
      case "BRANCH" => BitPat(true.B)
      case _        => BitPat(false.B)
    }
  }
}

class CL3Decoder extends Module {

  val io = IO(new Bundle {
    val inst  = Input(UInt(32.W))
    val pc    = Input(UInt(32.W))
    val dummy = Input(Bool())
    val out   = Output(new DEInfo())
  })

  import CL3InstInfo._

  val decodeTable  = new DecodeTable(instPatterns, allFields)
  val decodeResult = decodeTable.decode(io.inst)

  io.out.uop.op0 := decodeResult(OP0Field)
  io.out.uop.op1 := decodeResult(OP1Field)
  io.out.uop.op2 := decodeResult(OP2Field)
  io.out.illegal := decodeResult(IllegalField)
  io.out.wen     := decodeResult(WENField)
  io.out.isLSU   := decodeResult(LSUField)
  io.out.isMUL   := decodeResult(MULField)
  io.out.isDIV   := decodeResult(DIVField)
  io.out.isCSR   := decodeResult(CSRField)
  io.out.isEXU   := decodeResult(EXUField)
  io.out.isBr    := decodeResult(BRField)

  io.out.inst := io.inst

  // TODO: use BoringUtil API
  io.out.pc    := io.pc
  io.out.dummy := io.dummy

}
