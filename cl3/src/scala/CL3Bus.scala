package cl3

import chisel3._
import chisel3.util._

class Cl3ICacheReq extends Bundle {
  val addr       = Output(UInt(32.W))
  val flush      = Output(Bool())
  val priv       = Output(UInt(2.W))
  val invalidate = Output(Bool())
}

class Cl3ICacheResp extends Bundle {
  val err       = Input(UInt(2.W))
  val inst      = Input(UInt(64.W))
}

class Cl3ICacheIO extends Bundle {
  val req  = Decoupled(new Cl3ICacheReq)
  val resp = Flipped(Valid(new Cl3ICacheResp))
}

class Cl3DCacheReq extends Bundle {
  val addr       = Output(UInt(32.W))
  val wdata      = Output(UInt(32.W))
  val wen        = Output(Bool())
  val mask       = Output(UInt(4.W))
  val cacheable  = Output(Bool())
}

class Cl3DCacheResp extends Bundle {
  val rdata      = Input(UInt(32.W))
  val error      = Input(Bool())
  val loadFault  = Input(Bool())
  val storeFault = Input(Bool())
  val respTag    = Input(UInt(11.W))
}

class Cl3DCacheIO extends Bundle {
  val req  = Decoupled(new Cl3DCacheReq)
  val resp = Flipped(Valid(new Cl3DCacheResp))
}
