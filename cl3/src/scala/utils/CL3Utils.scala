package cl3

import chisel3._
import chisel3.util._

object SignExt {
  def apply(sig: UInt, len: Int): UInt = {
    val signBit = sig(sig.getWidth - 1)
    if (sig.getWidth >= len) sig(len - 1, 0) else signBit.asUInt ## Fill(len - sig.getWidth, signBit) ## sig
  }
}

object ZeroExt {
  def apply(sig: UInt, len: Int): UInt = {
    if (sig.getWidth >= len) sig(len - 1, 0) else 0.U((len - sig.getWidth).W) ## sig
  }
}

class LFSR(addrWidth: Int, initialValue: Int = 0x0001, tapValue: Int = 0xb400) extends Module {
  val io = IO(new Bundle {
    val alloc      = Input(Bool())
    val allocEntry = Output(UInt(addrWidth.W))
  })

  val lfsr = RegInit(initialValue.U(16.W))

  when(io.alloc) {
    // Galois LFSR: shift right, and if LSB is 1, XOR with tap value
    val nextLfsr = Mux(lfsr(0), (lfsr >> 1) ^ tapValue.U, lfsr >> 1)
    lfsr := nextLfsr
  }

  io.allocEntry := lfsr(addrWidth - 1, 0)
}

//See https://github.com/OSCPU/NutShell/blob/master/src/main/scala/utils/LookupTree.scala
object LookupTree {
  def apply[T <: Data](key: UInt, mapping: Iterable[(UInt, T)]): T =
    Mux1H(mapping.map(p => (p._1 === key, p._2)))
}

object LookupTreeDefault {
  def apply[T <: Data](key: UInt, default: T, mapping: Iterable[(UInt, T)]): T =
    MuxLookup(key, default)(mapping.toSeq)
}
