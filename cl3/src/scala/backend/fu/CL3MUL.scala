package cl3

import chisel3._
import chisel3.util._

class MULInput extends Bundle {
  val info = Input(new OpInfo)
  val hold = Input(Bool())
}

class MULOutput extends Bundle {
  val result = Output(UInt(32.W))
}

class MULIO extends Bundle {
  val in  = new MULInput
  val out = new MULOutput
}

class CL3MUL extends Module {

  val io = IO(new MULIO)

  dontTouch(io)

  val inst  = io.in.info.inst
  val func3 = inst(14, 12)

  val en = io.in.info.valid && !io.in.hold

  val func3_q = RegEnable(func3, en)
  val ra_q    = RegEnable(io.in.info.ra, en)
  val rb_q    = RegEnable(io.in.info.rb, en)

  val signext           = SignExt(_: UInt, 33)
  val zeroext           = ZeroExt(_: UInt, 33)
  val mulInputFuncTable = List(
    "b000".U -> (zeroext, zeroext),
    "b001".U -> (signext, signext),
    "b010".U -> (signext, zeroext),
    "b011".U -> (zeroext, zeroext)
  )

  val multiplier = Module(new BoothMultiplier(32))

  multiplier.io.ra := LookupTree(func3_q, mulInputFuncTable.map(p => (p._1, p._2._1(ra_q))))
  multiplier.io.rb := LookupTree(func3_q, mulInputFuncTable.map(p => (p._1, p._2._2(rb_q))))

  // TODO:
  multiplier.io.valid := true.B

  io.out.result := Mux(func3_q.orR, multiplier.io.res(63, 32), multiplier.io.res(31, 0))
}

class C32 extends Module {
  val io = IO(new Bundle {
    val a1  = Input(Bool())
    val a2  = Input(Bool())
    val cin = Input(Bool())
    val s   = Output(Bool())
    val c   = Output(Bool())
  })

  io.s := io.a1 ^ io.a2 ^ io.cin
  io.c := io.a1 & io.a2 | io.a1 & io.cin | io.a2 & io.cin
}

class C22 extends Module {
  val io = IO(new Bundle {
    val a1 = Input(Bool())
    val a2 = Input(Bool())
    val s  = Output(Bool())
    val c  = Output(Bool())
  })

  io.s := io.a1 ^ io.a2;
  io.c := io.a1 & io.a2;
}

class BoothMultiplier(len: Int) extends Module {

  val io = IO(new Bundle {
    val valid  = Input(Bool())
    val ra     = Input(UInt(33.W))
    val rb     = Input(UInt(33.W))
    val res    = Output(UInt(64.W))
    val finish = Output(Bool())
  })

  val in_vld  = io.valid
  val in_rdy  = Wire(Bool())
  val out_vld = Wire(Bool())

  val vld_set = in_vld
  val vld_clr = out_vld

  val boothReg_vld_en = vld_set | vld_clr
  val boothReg_vld_n  = vld_set | ~vld_clr
  val boothReg_vld    = RegEnable(boothReg_vld_n, 0.B, boothReg_vld_en)

  in_rdy  := ~boothReg_vld | vld_clr
  out_vld := boothReg_vld

  val MulUnit = Module(new BoothMul(len + 1))
  MulUnit.io.a         := io.ra
  MulUnit.io.b         := io.rb
  MulUnit.io.regEnable := vld_set

  io.res    := MulUnit.io.result
  io.finish := out_vld
}

class BoothMul(len: Int) extends Module {
  val io = IO(new Bundle {
    val a, b      = Input(UInt(len.W))
    val regEnable = Input(Bool())
    val result    = Output(UInt((2 * len).W))
  })

  val (a, b) = (io.a, io.b)

  val b_sext, bx2, neg_b, neg_bx2 = Wire(UInt((len + 1).W))
  b_sext  := b(len - 1) ## b
  bx2     := b << 1
  neg_b   := (~b_sext).asUInt
  neg_bx2 := neg_b << 1

  val columns: Array[Seq[Bool]] = Array.fill(2 * len)(Seq())

  var last_x = WireInit(0.U(3.W))

  for (i <- Range(0, len, 2)) {
    val x            = if (i == 0) Cat(a(1, 0), 0.U(1.W)) else if (i + 1 == len) Cat(a(i), a(i, i - 1)) else a(i + 1, i - 1)
    val pp_temp      = MuxLookup(x, 0.U)(
      Seq(
        1.U -> b_sext,
        2.U -> b_sext,
        3.U -> bx2,
        4.U -> neg_bx2,
        5.U -> neg_b,
        6.U -> neg_b
      )
    )
    val s            = pp_temp(len)
    val t            = MuxLookup(last_x, 0.U(2.W))(
      Seq(
        4.U -> 2.U(2.W),
        5.U -> 1.U(2.W),
        6.U -> 1.U(2.W)
      )
    )
    last_x = x
    val (pp, weight) = i match {
      case 0                                     =>
        (Cat(~s, s, s, pp_temp), 0)
      case n if (n == len - 1) || (n == len - 2) =>
        (Cat(~s, pp_temp, t), i - 2)
      case _                                     =>
        (Cat(1.U(1.W), ~s, pp_temp, t), i - 2)
    }

    for (j <- columns.indices) {
      if (j >= weight && j < (weight + pp.getWidth)) {
        columns(j) = columns(j) :+ pp(j - weight)
      }
    }
  }

  def addOneColumn(col: Seq[Bool]): (Seq[Bool], Seq[Bool]) = {
    var sum  = Seq[Bool]()
    var cout = Seq[Bool]()
    col.size match {
      case 1 =>
        sum = col
      case 2 =>
        val c22 = Module(new C22)
        c22.io.a1 := col(0)
        c22.io.a2 := col(1)
        sum = Seq(c22.io.s)
        cout = Seq(c22.io.c)
      case 3 =>
        val c32 = Module(new C32)
        c32.io.a1  := col(0)
        c32.io.a2  := col(1)
        c32.io.cin := col(2)
        sum = Seq(c32.io.s)
        cout = Seq(c32.io.c)
      case n =>
        val (s1, c1) = addOneColumn(col.take(3))
        val (s2, c2) = addOneColumn(col.drop(3))
        sum = s1 ++ s2
        cout = c1 ++ c2

    }
    (sum, cout)
  }

  def max(in: Iterable[Int]): Int = in.reduce((a, b) => if (a > b) a else b)
  def addAll(cols: Seq[Seq[Bool]], depth: Int): (UInt, UInt) = {
    val currentTotal = cols.map(_.size).sum
    // println(s"Current Depth: $depth, Elements: $currentTotal")

    if (max(cols.map(_.size)) <= 2) {
      val sum   = Cat(cols.map(_(0)).reverse)
      var k     = 0
      while (cols(k).size == 1) k = k + 1
      val carry = Cat(cols.drop(k).map(_(1)).reverse)
      (sum, Cat(carry, 0.U(k.W)))
    } else {
      val columns_next = Array.fill(2 * len)(Seq[Bool]())
      var cout         = Seq[Bool]()
      for (i <- cols.indices) {
        val (s, c) = addOneColumn(cols(i))
        columns_next(i) = s ++ cout
        cout = c
      }

      val needReg     = depth == 3
      val toNextLayer =
        if (needReg)
          columns_next.map(_.map(x => RegEnable(x, io.regEnable)))
        else
          columns_next
      addAll(toNextLayer.toSeq, depth + 1)
    }
  }

  val (sum, carry) = addAll(columns.toSeq, 0)
  io.result := sum + carry
}
