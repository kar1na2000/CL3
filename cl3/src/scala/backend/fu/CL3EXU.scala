package cl3

import chisel3._
import chisel3.util._
import SignExt._
import ZeroExt._

class EXUInput extends Bundle {
  val info = Input(new OpInfo)
  val hold = Input(Bool())
}

class EXUOutput extends Bundle {
  val info = Flipped(new ISEXUInput)
}

class EXUIO  extends Bundle                 {
  val in  = new EXUInput
  val out = new EXUOutput
}
class CL3EXU extends Module with OpConstant {

  val io = IO(new EXUIO)

  val inst = io.in.info.inst
  val Uimm = Cat(inst(31, 12), 0.U(12.W))
  val Iimm = SignExt(inst(31, 20), 32)
  val Bimm = SignExt(Cat(inst(31), inst(7), inst(30, 25), inst(11, 8), 0.U(1.W)), 32)
  val Jimm = SignExt(Cat(inst(31), inst(19, 12), inst(20), inst(30, 25), inst(24, 21), 0.U(1.W)), 32)

  val alu_input_a = MuxLookup(io.in.info.uop.op1, io.in.info.ra)(
    Seq(
      OP1_PC -> io.in.info.pc,
      OP1_Z  -> 0.U
    )
  )

  val alu_input_b = MuxLookup(io.in.info.uop.op2, 0.U)(
    Seq(
      OP2_IMMU -> Uimm,
      OP2_IMMI -> Iimm,
      OP2_IMMB -> Bimm,
      OP2_IMMJ -> Jimm,
      OP2_REG  -> io.in.info.rb
    )
  )

// TODO: check timing path: Issue -> ALU -> result
  val alu = Module(new CL3ALU)
  alu.io.op := io.in.info.uop.op0
  alu.io.a  := alu_input_a
  alu.io.b  := alu_input_b

  val alu_res = alu.io.res

  val isBr   = io.in.info.uop.op1.orR && !io.in.info.uop.op1.andR
  val isJal  = io.in.info.uop.op2.andR
  val isJalr = !io.in.info.uop.op0.orR

  val taken = isJal || isJalr || MuxLookup(io.in.info.uop.op1, false.B)(
    Seq(
      OP1_BEQ -> alu.io.eq,
      OP1_BLT -> alu.io.lt,
      OP1_BNE -> !alu.io.eq,
      OP1_BGE -> !alu.io.lt
    )
  )

  val br_target  = io.in.info.pc + Bimm
  val jmp_target = alu.io.res

  io.out.info.br.valid := io.in.info.valid && taken
  io.out.info.br.pc    := Mux(isJal || isJalr, jmp_target, br_target)
  io.out.info.br.priv  := 0.U

  val is_ret  = isJalr && io.in.info.raIdx === 1.U && !Iimm.andR
  val is_call = isJal && io.in.info.rdIdx === 1.U &&
    isJalr && io.in.info.rdIdx === 1.U && !is_ret

  val bp_q = RegInit(0.U.asTypeOf(new BpInfo))

  bp_q.valid      := io.in.info.valid && isBr
  bp_q.isTaken    := taken
  bp_q.isNotTaken := !taken
  bp_q.source     := io.in.info.pc

  // TODO: check unaligned jump
  bp_q.target := Mux(isJal || isJalr, jmp_target, br_target)

  bp_q.isCall := is_call
  bp_q.isRet  := is_ret
  bp_q.isJmp  := isJal || isJalr

  io.out.info.bp := bp_q

  val result_q = RegInit(0.U(32.W))
  when(!io.in.hold) {
    result_q := Mux(isJal || isJalr, io.in.info.pc + 4.U, alu_res)
  }

  io.out.info.result := result_q
}

class CL3ALU extends Module with OpConstant {
  val io         = IO(new Bundle {
    val a   = Input(UInt(32.W))
    val b   = Input(UInt(32.W))
    val op  = Input(UInt(OP0_WIDTH.W))
    val res = Output(UInt(32.W))
    val eq  = Output(Bool())
    val lt  = Output(Bool())
  })
// TODO: Refactor the decoding logic. 'slt' can be implemented using 'sub'.
  val aluop_add  = io.op === OP0_ADD || io.op === OP0_NONE
  val aluop_sub  = io.op === OP0_SUB
  val aluop_and  = io.op === OP0_AND
  val aluop_or   = io.op === OP0_OR
  val aluop_xor  = io.op === OP0_XOR
  val aluop_sll  = io.op === OP0_SLL
  val aluop_slt  = io.op === OP0_SLT
  val aluop_sltu = io.op === OP0_SLTU
  val aluop_srl  = io.op === OP0_SRL
  val aluop_sra  = io.op === OP0_SRA

  val adder_opadd = aluop_add
  val adder_opsub = aluop_sub | aluop_sltu | aluop_slt
  val adder_unsig = aluop_sltu

  val adder_op1 = Wire(UInt(33.W))
  adder_op1 := (~adder_unsig & io.a(31)) ## io.a
  val adder_op2 = Wire(UInt(33.W))
  adder_op2 := (~adder_unsig & io.b(31)) ## io.b
  val adder_cin = adder_opsub
  val adder_res = Wire(UInt(33.W))
  adder_res := adder_op1 + (Fill(33, adder_opsub).asUInt ^ adder_op2) + adder_cin

  val slt_res = adder_res(32)

  val and_res = io.a & io.b
  val or_res  = io.a | io.b
  val xor_res = io.a ^ io.b

  val srl_op1 = Mux(aluop_sll, Reverse(io.a), io.a)
  val srl_op2 = io.b(4, 0)

  // TODO: try to use Chisel utility to do this
  val srl_res  = srl_op1 >> srl_op2
  val sll_res  = Reverse(srl_res)
  val sra_mask = ~(Fill(32, true.B).asUInt >> srl_op2)
  val sra_res  = srl_res | sra_mask & Fill(32, io.a(31)).asUInt

  io.res := Fill(32, aluop_add | aluop_sub).asUInt & adder_res |
    Fill(32, aluop_slt | aluop_sltu).asUInt & (Fill(31, false.B) ## slt_res) |
    Fill(32, aluop_and) & and_res |
    Fill(32, aluop_or) & or_res |
    Fill(32, aluop_xor) & xor_res |
    Fill(32, aluop_sll) & sll_res |
    Fill(32, aluop_srl) & srl_res |
    Fill(32, aluop_sra) & sra_res

  io.eq := ~xor_res.orR
  io.lt := slt_res
}
