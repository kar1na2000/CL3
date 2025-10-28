package cl3

import chisel3._
import chisel3.util._

class AxiArbiterIO(implicit p: AXI4Params) extends Bundle {
    val icache_axi = Flipped(new Axi4MasterIO(p)) // ICache
    val dcache_axi = Flipped(new Axi4MasterIO(p)) // DCache
    val mem_axi    = Flipped(new Axi4SlaveIO(p))  // Downstream Memory
}