package cl3

import chisel3._
import chisel3.util._

class ICacheCpuIO(p: ICacheParams) extends Bundle {
  // Inputs
  val req_rd         = Input(Bool())
  val req_flush      = Input(Bool())
  val req_invalidate = Input(Bool())
  val req_pc         = Input(UInt(p.pcBits.W))


  // Outputs
  val resp_accept     = Output(Bool())
  val resp_valid      = Output(Bool())
  val resp_error      = Output(Bool())
  val resp_inst       = Output(UInt(p.instBits.W))
}

class ICacheIO(p: ICacheParams) extends Bundle {
  val cpu = new ICacheCpuIO(p)
  val axi = new Axi4MasterIO(p.axi)
}

class ICacheDataRamIO(p: ICacheParams) extends Bundle {
  val addr = Input(UInt(p.dataRamIdxBits.W))
  val din  = Input(UInt(p.dataRamDataBits.W))
  val wr   = Input(Bool())
  val dout = Output(UInt(p.dataRamDataBits.W))
}

class ICacheTagRamIO(p: ICacheParams) extends Bundle {
  val addr = Input(UInt(p.tagRamIdxBits.W))
  val din  = Input(UInt(p.tagRamDataBits.W))
  val wr   = Input(Bool())
  val dout = Output(UInt(p.tagRamDataBits.W))
}
