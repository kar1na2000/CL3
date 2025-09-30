package cl3

import chisel3._
import chisel3.util._

class SimpleReqArbiter extends Module {
  val io = IO(new Bundle {
    val ifuReq = Flipped(Decoupled(Input(new SimpleMemReq(32))))
    val lsuReq = Flipped(Decoupled(Input(new SimpleMemReq(32))))
    val outReq = Decoupled(new SimpleMemReq(32))
    val isIFU  = Output(Bool())
  })

  val sIdle :: sIfu :: sLsu :: Nil = Enum(3)
  val state                        = RegInit(sIdle)

  val stateIdle: Bool = state === sIdle
  val stateIFU:  Bool = state === sIfu
  val stateLSU:  Bool = state === sLsu

  val chooseIFU = io.ifuReq.valid && !io.lsuReq.valid
  val chooseLSU = io.lsuReq.valid

  when(stateIdle) {
    when(chooseIFU && !io.outReq.fire) {
      state := sIfu
    }.elsewhen(chooseLSU && !io.outReq.fire) {
      state := sLsu
    }
  }

  when(stateIFU) {
    when(io.outReq.fire) {
      state := sIdle
    }
  }

  when(stateLSU) {
    when(io.outReq.fire) {
      state := sIdle
    }
  }

  io.outReq.bits := Mux1H(
    Seq(
      stateIdle -> Mux(chooseIFU, io.ifuReq.bits, io.lsuReq.bits),
      stateIFU  -> io.ifuReq.bits,
      stateLSU  -> io.lsuReq.bits
    )
  )

  io.outReq.valid := Mux1H(
    Seq(
      stateIdle -> (chooseIFU || chooseLSU),
      stateIFU  -> true.B,
      stateLSU  -> true.B
    )
  )

  io.ifuReq.ready := Mux1H(
    Seq(
      stateIdle -> Mux(chooseIFU, io.outReq.ready, false.B),
      stateIFU  -> io.outReq.ready,
      stateLSU  -> false.B
    )
  )

  io.lsuReq.ready := Mux1H(
    Seq(
      stateIdle -> Mux(chooseLSU, io.outReq.ready, false.B),
      stateIFU  -> false.B,
      stateLSU  -> io.outReq.ready
    )
  )

  io.isIFU := Mux1H(
    Seq(
      stateIdle -> Mux(chooseIFU, true.B, false.B),
      stateIFU  -> true.B,
      stateLSU  -> false.B
    )
  )

}

class SimpleRespArbiter extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(Decoupled(Input(new SimpleMemResp(32))))
    val ifu   = Decoupled(Output(new SimpleMemResp(32)))
    val lsu   = Decoupled(Output(new SimpleMemResp(32)))
    val isIFU = Input(Bool())
  })

  val chooseIFU = io.in.valid && io.isIFU
  val chooseLSU = io.in.valid && !io.isIFU

  io.ifu.valid := Mux(chooseIFU, io.in.valid, false.B)
  io.ifu.bits  := Mux(chooseIFU, io.in.bits, 0.U.asTypeOf(new SimpleMemResp(32)))

  io.lsu.valid := Mux(chooseLSU, io.in.valid, false.B)
  io.lsu.bits  := Mux(chooseLSU, io.in.bits, 0.U.asTypeOf(new SimpleMemResp(32)))

  io.in.ready := Mux(chooseIFU, io.ifu.ready, Mux(chooseLSU, io.lsu.ready, false.B))
}
