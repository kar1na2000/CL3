package cl3

import chisel3._
import chisel3.util._

class SimpleMemReq(xlen: Int) extends Bundle {
  val addr      = UInt(xlen.W)
  val wen       = Bool()
  val size      = UInt(3.W) // 3 bits in AXI4
  val wdata     = UInt(xlen.W)
  val mask      = UInt(4.W)
  val cacheable = Bool()

  def connect(source: SimpleMemReq): Unit = {
    this.addr      := source.addr
    this.wen       := source.wen
    this.size      := source.size
    this.wdata     := source.wdata
    this.mask      := source.mask
    this.cacheable := source.cacheable
  }

}

class SimpleMemResp(xlen: Int) extends Bundle {
  val err   = UInt(4.W)
  val rdata = UInt(xlen.W)
}

class CL3ICacheReq extends SimpleMemReq(32) {
  val flush      = Output(Bool())
  val invalidate = Output(Bool())
}

class CL3ICacheResp extends SimpleMemResp(64) {}

class CL3ICacheIO extends Bundle {
  val req  = Decoupled(new CL3ICacheReq)
  val resp = Flipped(Valid(new CL3ICacheResp))
}

class CL3DCacheReq extends SimpleMemReq(32) {}

class CL3DCacheResp extends SimpleMemResp(32) {}

class CL3DCacheIO extends Bundle {
  val req  = Decoupled(new CL3DCacheReq)
  val resp = Flipped(Valid(new CL3DCacheResp))
}
