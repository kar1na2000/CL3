package cl3

import chisel3._
import chisel3.util._

object SramAddr {
    @inline def bankSel(addr: UInt, bankSelBits: Int = 2): UInt =
        addr(bankSelBits - 1, 0)

    @inline def rowAddr(addr: UInt, bankSelBits: Int = 2): UInt =
        addr >> bankSelBits          

    @inline def byteMaskToBitMask(byteMask: UInt, dataBits: Int = 32): UInt = {
        val bytes = dataBits / 8
        // Cat(Seq.tabulate(bytes) { i =>
        //     Fill(8, byteMask(i))
        // }.reverse)
        Cat((0 until bytes).reverse.map(i => Fill(8, !byteMask(i))))
    }
}