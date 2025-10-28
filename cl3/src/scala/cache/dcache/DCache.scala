package cl3

import chisel3._
import chisel3.util._

class DCache(p: DCacheParams) extends Module {
    val io = IO(new DCacheTopIO(p))

    val u_core = Module(new dcache_core(p))
    val u_pmem_mux = Module(new dcache_pmem_mux(p))
    val u_uncached = Module(new dcache_if_pmem(p))
    val u_axi = Module(new dcache_axi(p))
    val u_mux = Module(new dcache_mux(p))
    
    u_mux.io.backs.uncached <> u_uncached.io.cpu
    u_uncached.io.pmem <> u_pmem_mux.io.in0port

    u_pmem_mux.io.outport <> u_axi.io.pmem
    u_pmem_mux.io.select_i := u_mux.io.cacheActive
    
    u_mux.io.cpu <> io.cpu

    u_core.io.pmem <> u_pmem_mux.io.in1port
    u_core.io.cpu  <> u_mux.io.backs.cached
    u_core.io.amo  <> io.amo

    io.axi <> u_axi.io.axi

}