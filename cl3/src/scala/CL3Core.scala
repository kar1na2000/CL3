package cl3

import chisel3._
import chisel3.util._

class CoreIO extends Bundle {
  val imem = new CL3ICacheIO
  val dmem = new CL3DCacheIO
}

class CL3Core extends Module with CL3Config {

  val io = IO(new CoreIO)

  val frontend = Module(new CL3Frontend)

  val mmu = Module(new CL3MMU)
  mmu.io.fetchIn <> frontend.io.mem
  mmu.io.fetchOut <> io.imem
  mmu.io.lsuOut <> io.dmem

  val issue = Module(new CL3Issue)
  issue.io.in.fetch <> frontend.io.out
  frontend.io.bp  := issue.io.out.bp
  frontend.io.br  := issue.io.out.br
  issue.io.in.irq := false.B

  val lsu = Module(new CL3LSU)
  lsu.io.in.mem   := mmu.io.lsuIn.resp
  mmu.io.lsuIn.req <> lsu.io.out.mem
  mmu.io.lsuIn.resp <> lsu.io.in.mem
  lsu.io.in.info <> issue.io.out.op(2)
  issue.io.in.lsu := lsu.io.out.info

  val csr = Module(new CL3CSR)
  csr.io.in.bootAddr := 0.U
  csr.io.in.irq      := false.B
  csr.io.in.info     := issue.io.out.op(5)
  csr.io.in.wb       := issue.io.out.csr
  csr.io.in.bootAddr := BootAddr
  issue.io.in.csr    := csr.io.out.info
  mmu.io.ctrl        := csr.io.out.mmu

  val mul = Module(new CL3MUL)
  mul.io.in.hold         := issue.io.out.hold
  mul.io.in.info         := issue.io.out.op(3)
  issue.io.in.mul.result := mul.io.out.result

  val div = Module(new CL3DIV)
  div.io.in.info  := issue.io.out.op(4)
  issue.io.in.div := div.io.out.wb

  val exec0 = Module(new CL3EXU)
  exec0.io.in.hold    := issue.io.out.hold
  exec0.io.in.info    := issue.io.out.op(0)
  issue.io.in.exec(0) := exec0.io.out.info

  val exec1 = Module(new CL3EXU)
  exec1.io.in.hold    := issue.io.out.hold
  exec1.io.in.info    := issue.io.out.op(1)
  issue.io.in.exec(1) := exec1.io.out.info

}
