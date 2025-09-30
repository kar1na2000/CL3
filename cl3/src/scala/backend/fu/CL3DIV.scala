package cl3

import chisel3._
import chisel3.util._

class DIVInput extends Bundle {
  val info = Input(new OpInfo)
}

class DIVOutput extends Bundle {
  val wb = Flipped(new PipeDIVInput)
}

class DIVIO extends Bundle {
  val in  = new DIVInput
  val out = new DIVOutput
}

class CL3DIV extends Module {

  val io = IO(new DIVIO)

  val inst = io.in.info.inst

  val func3 = inst(14, 12)

  val div  = func3 === "b100".U
  val divu = func3 === "b101".U
  val rem  = func3 === "b110".U
  val remu = func3 === "b111".U

  val divider = Module(new RestoringDivider(32))

  divider.io.valid := io.in.info.valid
  divider.io.ra    := io.in.info.ra
  divider.io.rb    := io.in.info.rb
  divider.io.sign  := div | rem

  val div_q = RegEnable(rem | remu, io.in.info.valid)

  io.out.wb.valid := divider.io.finish

  io.out.wb.result := Mux(div_q, divider.io.res(63, 32), divider.io.res(31, 0))

}

class RestoringDivider(len: Int = 32) extends Module {

  val io = IO(new Bundle {
    val valid  = Input(Bool())
    val ra     = Input(UInt(32.W))
    val rb     = Input(UInt(32.W))
    val sign   = Input(Bool())
    val res    = Output(UInt(64.W))
    val finish = Output(Bool())

  })

  def abs(a: UInt, sign: Bool): (Bool, UInt) = {
    val s = a(len - 1) && sign
    (s, Mux(s, -a, a))
  }

  val s_idle :: s_compute :: s_finish :: Nil = Enum(3)
  val state                                  = RegInit(s_idle)

  val dividend = io.ra
  val divisor  = io.rb
  val divBy0   = divisor === 0.U(len.W)

  val shiftReg = Reg(UInt((1 + len * 2).W))
  val hi       = shiftReg(len * 2, len)
  val lo       = shiftReg(len - 1, 0)

  val (dividendSign, dividendVal) = abs(dividend, io.sign)
  val (divisorSign, divisorVal)   = abs(divisor, io.sign)
  val dividendSignReg             = RegEnable(dividendSign, io.valid)
  val quotientSignReg             = RegEnable((dividendSign ^ divisorSign) && !divBy0, io.valid)
  val divisorReg                  = RegEnable(divisorVal, io.valid)
  val dividendReg                 = RegEnable(dividend, io.valid)

  val cnt = Counter(len)

  when(io.valid) {
    when(divBy0) {
      state := s_finish
    }.otherwise {
      state := s_compute
    }
    shiftReg  := 0.U((len).W) ## dividendVal ## 0.U(1.W)
    cnt.value := 0.U
    // TODO: We can optimize the performance of division by calculating leading '0'.
  }.elsewhen(state === s_compute) {
    val enough = hi.asUInt >= divisorReg.asUInt
    shiftReg := Cat(Mux(enough, hi - divisorReg, hi)(len - 1, 0), lo, enough)
    cnt.inc()
    when(cnt.value === (len - 1).U) { state := s_finish }
  }.elsewhen(state === s_finish) {
    state := s_idle
  }

  val r = hi(len, 1)

  val zero      = !divisorReg.orR
  val Quotient  = Mux(zero, "hffffffff".U, Mux(quotientSignReg, -lo, lo))
  val Remainder = Mux(zero, dividendReg, Mux(dividendSignReg, -r, r))
  io.res := Cat(Remainder, Quotient)

  io.finish := state === s_finish
}
