package cl3

import chisel3._
import chisel3.util._

class CacheSubSystem(implicit p: AXI4Params, dp: DCacheParams, ip: ICacheParams) extends Module {
    val io = IO(new Bundle {
        val dc_cpu = new CpuMemPort(dp)
        val ic_cpu = new ICacheCpuIO(ip)
        val amo = new AmoBundle()
        val mem = Flipped(new Axi4SlaveIO(p))
    })

    val u_icache = Module(new ICache(ip))
    val u_dcache = Module(new DCache(dp))
    val u_arbiter = Module(new AxiArbiter())

    u_icache.io.cpu <> io.ic_cpu
    u_dcache.io.cpu <> io.dc_cpu
    u_dcache.io.amo <> io.amo

    u_arbiter.io.icache_axi <> u_icache.io.axi
    u_arbiter.io.dcache_axi <> u_dcache.io.axi
    io.mem <> u_arbiter.io.mem_axi
}