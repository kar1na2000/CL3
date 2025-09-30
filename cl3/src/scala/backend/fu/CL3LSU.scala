package cl3

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import cl3.CL3InstInfo._

class LSUInput extends Bundle {
  val info = Input(new OpInfo)
  val mem  = Flipped(Valid(new CL3DCacheResp))
}

class LSUOutput extends Bundle {
  val mem   = Decoupled(new CL3DCacheReq)
  val stall = Output(Bool())
  val info  = Flipped(new PipeLSUInput)
}

class LSUIO extends Bundle {
  val in  = new LSUInput
  val out = new LSUOutput
}

//TODO: check the timing path: issue -> lsu
//TODO: add exception support
class CL3LSU extends Module with LSUConstant {

  val io = IO(new LSUIO)

  val table = new DecodeTable(instPatterns, Seq(LSUOPField))
  val res   = table.decode(io.in.info.inst)
  val op    = res(LSUOPField)

  val inst = io.in.info.inst

  val Iimm = SignExt(inst(31, 20), 32)
  val Simm = SignExt(Cat(inst(31, 25), inst(11, 7)), 32)

  val is_load  = !op(LSU_LS_BIT)
  val is_store = op(LSU_LS_BIT)

  val addr = io.in.info.ra + Mux(is_load, Iimm, Simm)

  // TODO: refactor MuxLookup to improve timing
  val mask = MuxLookup(addr(1, 0) ## op(3, 2), MASK_Z)(
    Seq(
      // 1 byte
      "b0001".U -> MASK_B0,
      "b0101".U -> MASK_B1,
      "b1001".U -> MASK_B2,
      "b1101".U -> MASK_B3,

      // 2 bytes
      "b0010".U -> MASK_LO,
      "b1010".U -> MASK_HI,

      // 4 bytes
      "b0011".U -> MASK_ALL
    )
  )

  val outstanding_q = RegInit(false.B)

  when(io.out.mem.fire) {
    outstanding_q := true.B
  }.elsewhen(io.in.mem.valid) {
    outstanding_q := false.B
  }

  val pending = outstanding_q && !io.in.mem.valid

  val req_q       = RegInit(0.U.asTypeOf(new CL3DCacheReq))
  val op_q        = RegInit(0.U(4.W))
  val req_valid_q = RegInit(false.B)

  when(io.in.info.valid && !(io.out.mem.valid && !io.out.mem.ready)) {
    req_valid_q := true.B

    req_q.addr      := addr
    req_q.wdata     := io.in.info.rb
    req_q.wen       := is_store
    req_q.mask      := mask
    req_q.cacheable := false.B // TODO:

    op_q := op

  }.elsewhen(io.out.mem.fire && !io.in.info.valid) {
    req_valid_q := false.B
  }

  io.out.mem.valid := req_valid_q
  io.out.mem.bits  := req_q

  // TODO: refactor MuxLookup to improve timing
  io.out.mem.bits.wdata := MuxLookup(req_q.mask, req_q.wdata)(
    Seq(
      MASK_B1 -> 0.U(16.W) ## req_q.wdata(7, 0) ## 0.U(8.W),
      MASK_B2 -> 0.U(8.W) ## req_q.wdata(7, 0) ## 0.U(16.W),
      MASK_B3 -> req_q.wdata(7, 0) ## 0.U(24.W),
      MASK_HI -> req_q.wdata(15, 0) ## 0.U(16.W)
    )
  )

  io.out.mem.bits := req_q

  class ReqRecord extends Bundle {
    val mask = UInt(4.W)
    val op   = UInt(4.W)
  }

  val req_record_q = RegInit(0.U.asTypeOf(new ReqRecord))
  when(io.out.mem.fire) {
    req_record_q.mask := req_q.mask
    req_record_q.op   := op_q
  }

  val lb_data = Mux1H(
    Seq(
      req_record_q.mask(0) -> io.in.mem.bits.rdata(7, 0),
      req_record_q.mask(1) -> io.in.mem.bits.rdata(15, 8),
      req_record_q.mask(2) -> io.in.mem.bits.rdata(23, 16),
      req_record_q.mask(3) -> io.in.mem.bits.rdata(31, 24)
    )
  )

  val lh_data = Mux(req_record_q.mask(1, 0).andR, io.in.mem.bits.rdata(15, 0), io.in.mem.bits.rdata(31, 16))

  val lw_data = io.in.mem.bits.rdata

  io.out.info.rdata := MuxLookup(req_record_q.op(2, 0), io.in.mem.bits.rdata)(
    Seq(
      "b010".U -> SignExt(lb_data, 32),
      "b011".U -> ZeroExt(lb_data, 32),
      "b100".U -> SignExt(lh_data, 32),
      "b101".U -> ZeroExt(lh_data, 32)
    )
  )

  io.out.info.valid  := io.in.mem.valid && outstanding_q
  io.out.info.except := 0.U // TODO:
  io.out.stall       := pending || io.out.mem.valid && !io.out.mem.ready

}

object LSUOPField extends DecodeField[InstructionPattern, UInt] {
  def name: String = "LSU OP"

  import LSUConstant._
  def chiselType: UInt = UInt(LSU_WIDTH.W)

  def genTable(op: InstructionPattern): BitPat = {

    op.name match {
      case "lw"  => BitPat(LSU_LW)
      case "lh"  => BitPat(LSU_LH)
      case "lhu" => BitPat(LSU_LHU)
      case "lb"  => BitPat(LSU_LB)
      case "lbu" => BitPat(LSU_LBU)
      case "sw"  => BitPat(LSU_SW)
      case "sh"  => BitPat(LSU_SH)
      case "sb"  => BitPat(LSU_SB)
      case _     => BitPat.dontCare(LSU_WIDTH)
    }
  }
}
