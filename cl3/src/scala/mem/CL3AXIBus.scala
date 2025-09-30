package cl3

import chisel3._
import chisel3.util._

class SimpleAXI4MasterBundle(ADDR_WIDTH: Int, DATA_WIDTH: Int, ID_WIDTH: Int) extends Bundle {

  val aw = Decoupled(new Bundle {
    val awaddr  = Output(UInt(ADDR_WIDTH.W))
    val awid    = Output(UInt(ID_WIDTH.W))
    val awlen   = Output(UInt(8.W))
    val awsize  = Output(UInt(3.W))
    val awburst = Output(UInt(2.W))
    val awlock  = Output(UInt(1.W))
    val awcache = Output(UInt(4.W))
    val awprot  = Output(UInt(3.W))
  })

  val w = Decoupled(new Bundle {
    val wdata = Output(UInt(DATA_WIDTH.W))
    val wstrb = Output(UInt((DATA_WIDTH / 8).W))
    val wlast = Output(Bool())
  })

  val b = Flipped(Decoupled(new Bundle {
    val bresp = Input(UInt(2.W))
    val bid   = Input(UInt(ID_WIDTH.W))

  }))

  val ar = Decoupled(new Bundle {
    val araddr  = Output(UInt(ADDR_WIDTH.W))
    val arid    = Output(UInt(ID_WIDTH.W))
    val arlen   = Output(UInt(8.W))
    val arsize  = Output(UInt(3.W))
    val arburst = Output(UInt(2.W))
    val arlock  = Output(UInt(1.W))
    val arcache = Output(UInt(4.W))
    val arprot  = Output(UInt(3.W))
  })

  val r = Flipped(Decoupled(new Bundle {
    val rresp = Input(UInt(2.W))
    val rdata = Input(UInt(DATA_WIDTH.W))
    val rlast = Input(Bool())
    val rid   = Input(UInt(ID_WIDTH.W))

  }))
}

class Cache2AXI4Bridge extends Module with AXIConstant {

  val io = IO(new Bundle {
    val rdReq       = Flipped(Decoupled(Input(new SimpleMemReq(32))))
    val wrReq       = Flipped(Decoupled(Input(new SimpleMemReq(32))))
    val rdResp      = Decoupled(Output(new SimpleMemResp(32)))
    val wrResp      = Decoupled(Output(new SimpleMemResp(32)))
    val rdReqIsIFU  = Input(Bool())
    val rdRespIsIFU = Output(Bool())
    val axi4        = new SimpleAXI4MasterBundle(ADDR_WIDTH = 32, DATA_WIDTH = 32, ID_WIDTH = 4)
  })

  dontTouch(io)

  val sRdIdle :: sWaitingRChannel :: Nil = Enum(2)
  val rdState                            = RegInit(sRdIdle)

  val stateRdIdle:          Bool = rdState === sRdIdle
  val stateWaitingRChannel: Bool = rdState === sWaitingRChannel

  when(stateRdIdle) {
    when(io.axi4.ar.fire) {
      rdState := sWaitingRChannel
    }
  }

  when(stateWaitingRChannel) {
    when(io.axi4.r.fire && io.axi4.r.bits.rlast) {
      rdState := sRdIdle
    }
  }

  val sWrIdle :: sWaitingWChannel :: sWaitingBChannel :: Nil = Enum(3)
  val wrState                                                = RegInit(sWrIdle)

  val stateWrIdle:          Bool = wrState === sWrIdle
  val stateWaitingWChannel: Bool = wrState === sWaitingWChannel
  val stateWaitingBChannel: Bool = wrState === sWaitingBChannel

  val wrAddrReg = RegInit(0.U(28.W))

  when(stateWrIdle) {
    when(io.axi4.aw.fire) {
      wrState   := sWaitingWChannel
      wrAddrReg := io.wrReq.bits.addr(31, 4)
    }
  }

  when(stateWaitingWChannel) {
    when(io.axi4.w.fire && io.axi4.w.bits.wlast) {
      wrState := sWaitingBChannel
    }
  }

  when(stateWaitingBChannel) {
    when(io.axi4.b.fire) {
      wrState := sWrIdle
    }
  }

  val rdCacheable = io.rdReq.bits.cacheable
  val wrCacheable = io.wrReq.bits.cacheable

  // AXI4 AR CHANNEl
  io.axi4.ar.valid        := io.rdReq.valid && stateRdIdle
  io.axi4.ar.bits.araddr  := io.rdReq.bits.addr
  io.axi4.ar.bits.arburst := Mux(rdCacheable, AXI_INCR, AXI_FIXED)
  io.axi4.ar.bits.arcache := 0.U
  io.axi4.ar.bits.arid    := Mux(io.rdReqIsIFU, 0.U, 1.U)
  io.axi4.ar.bits.arlen   := Mux(rdCacheable, 3.U, 0.U)
  io.axi4.ar.bits.arlock  := 0.U
  io.axi4.ar.bits.arprot  := 0.U
  io.axi4.ar.bits.arsize  := io.rdReq.bits.size

  // AXI4 AW CHANNEL
  io.axi4.aw.valid        := io.wrReq.valid && stateWrIdle
  io.axi4.aw.bits.awaddr  := io.wrReq.bits.addr
  io.axi4.aw.bits.awburst := Mux(wrCacheable, AXI_INCR, AXI_FIXED)
  io.axi4.aw.bits.awcache := 0.U
  io.axi4.aw.bits.awid    := 1.U
  io.axi4.aw.bits.awlen   := Mux(wrCacheable, 3.U, 0.U)
  io.axi4.aw.bits.awlock  := 0.U
  io.axi4.aw.bits.awprot  := 0.U
  io.axi4.aw.bits.awsize  := 2.U

  // AXI4 W CHANNEL
  val cnt = RegInit(0.U(2.W))
  when(io.axi4.w.fire) {
    cnt := cnt + 1.U
  }.elsewhen(io.axi4.b.fire) {
    cnt := 0.U
  }
  io.axi4.w.valid := io.wrReq.valid && stateWaitingWChannel
  io.axi4.w.bits.wdata := io.wrReq.bits.wdata
  io.axi4.w.bits.wlast := Mux(wrCacheable, cnt === 3.U, true.B)
  io.axi4.w.bits.wstrb := io.wrReq.bits.mask

  val rdReady = Mux1H(
    Seq(
      stateRdIdle          -> io.axi4.ar.ready,
      stateWaitingRChannel -> false.B
    )
  )
  val wrReady = Mux1H(
    Seq(
      stateWrIdle          -> io.axi4.aw.ready,
      stateWaitingWChannel -> io.axi4.w.ready,
      stateWaitingBChannel -> false.B
    )
  )

  val conflict1 = io.wrReq.valid && io.rdReq.valid &&
    io.wrReq.bits.addr(31, 4) === io.rdReq.bits.addr(31, 4)

  val conflict2 = !stateWrIdle && io.rdReq.valid &&
    wrAddrReg === io.rdReq.bits.addr(31, 4)

  io.rdReq.ready := rdReady && !conflict1 && !conflict2
  io.wrReq.ready := wrReady

  val rdRespBuffer  = Reg(new SimpleMemResp(32))
  val wrRespBuffer  = Reg(new SimpleMemResp(32))
  val rdBufferValid = RegInit(false.B)
  val wrBufferValid = RegInit(false.B)

  val isValidRdResp = io.axi4.r.valid && stateWaitingRChannel
  val rdIsIFU       = io.axi4.r.valid && !io.axi4.r.bits.rid(0)
  val rdIsLSU       = io.axi4.r.valid && io.axi4.r.bits.rid(0)
  val isValidWrResp = io.axi4.b.valid && stateWaitingBChannel
  val wrIsIFU       = io.axi4.b.valid && !io.axi4.b.bits.bid(0)
  val wrIsLSU       = io.axi4.b.valid && io.axi4.b.bits.bid(0)

  val rdBufferReady = !rdBufferValid || rdBufferValid && io.rdResp.fire
  val wrBufferReady = !wrBufferValid || wrBufferValid && io.wrResp.fire
  when(isValidRdResp && rdBufferReady) {
    rdRespBuffer.rdata := io.axi4.r.bits.rdata
    rdRespBuffer.err   := io.axi4.r.bits.rresp
    rdBufferValid      := true.B
  }.elsewhen(io.rdResp.fire) {
    rdBufferValid := false.B
  }

  when(isValidWrResp && wrBufferReady) {
    wrRespBuffer.rdata := 0.U
    wrRespBuffer.err   := io.axi4.b.bits.bresp
    wrBufferValid      := true.B
  }.elsewhen(io.wrResp.fire) {
    wrBufferValid := false.B
  }

  // AXI4 R CHANNEL
  io.axi4.r.ready := stateWaitingRChannel && rdBufferReady

  // AXI4 B CHANNEL
  io.axi4.b.ready := stateWaitingBChannel && wrBufferReady

  io.rdResp.valid := rdBufferValid
  io.rdResp.bits  := rdRespBuffer
  io.wrResp.valid := wrBufferValid
  io.wrResp.bits  := wrRespBuffer

  val isIFU = RegInit(false.B)
  when(io.axi4.ar.fire) {
    isIFU := io.rdReqIsIFU
  }
  io.rdRespIsIFU := isIFU
}

class AXIWidthConverter extends Module with AXIConstant {

  val io = IO(new Bundle {
    val in  = Flipped(new SimpleAXI4MasterBundle(32, 32, 4))
    val out = new SimpleAXI4MasterBundle(32, 64, 4)
  })

  // AW Channel
  io.out.aw <> io.in.aw // awaddr 32 to 64 here

  val awRegEnable = RegInit(false.B)
  val awReg       = RegInit(false.B)
  val awINCRReg   = RegInit(false.B)

  val hiAWAddrBit = io.out.aw.bits.awaddr(2) // low 32bit or hi 32bit in 64bit bus

  // We assume that the AW and W channels always belong to the same write transaction.
  when(io.out.aw.fire && !io.out.w.fire) {
    awRegEnable := true.B
    awReg       := hiAWAddrBit
    awINCRReg   := io.out.aw.bits.awburst === AXI_INCR
  }.elsewhen(io.out.w.fire && io.out.w.bits.wlast) {
    awRegEnable := false.B
  }

  val zero = WireDefault(0.U(32.W))

  val write_order_q = RegInit(false.B)

  val write_order_w = Mux(awRegEnable, write_order_q, hiAWAddrBit)

  when(io.out.aw.fire) {
    write_order_q := hiAWAddrBit
  }.elsewhen(awINCRReg && io.out.w.fire) {
    write_order_q := ~write_order_q
  }
  // val laneOrder = Mux(awRegEnable, awReg, hiAWAddrBit)
  val wdata = io.in.w.bits.wdata
  val wstrb = io.in.w.bits.wstrb

  // W Channel
  io.out.w.valid      := io.in.w.valid
  io.out.w.bits.wdata := Mux(write_order_w, Cat(wdata, zero), Cat(zero, wdata))
  io.out.w.bits.wlast := io.in.w.bits.wlast
  io.out.w.bits.wstrb := Mux(write_order_w, Cat(wstrb, 0.U(4.W)), Cat(0.U(4.W), wstrb))
  io.in.w.ready       := io.out.w.ready

  // B Channel
  io.out.b.ready     := io.in.b.ready
  io.in.b.bits.bresp := io.out.b.bits.bresp
  io.in.b.bits.bid   := io.out.b.bits.bid
  io.in.b.valid      := io.out.b.valid

  // AR Channel
  io.out.ar <> io.in.ar // araddr 32 to 64 here

  val arRegEnable    = RegInit(false.B)
  val arByteOrderReg = RegInit(false.B)
  val arBurstReg     = RegInit(false.B) // Fixed or not

  val hiARAddrBit = io.out.ar.bits.araddr(2)

  when(io.out.ar.fire) {
    arRegEnable    := true.B
    arByteOrderReg := hiARAddrBit
    arBurstReg     := io.in.ar.bits.arburst === AXI_FIXED
  }.elsewhen(io.out.r.fire && io.out.r.bits.rlast) {
    arRegEnable := false.B
  }

  when(io.out.r.fire && !arBurstReg) {
    arByteOrderReg := ~arByteOrderReg
  }

  // assert(~(io.out.r.valid && io.out.ar.valid && arRegEnable))

  // val rdHi = Mux(arRegEnable, arReg, hiARAddrBit)
  val rdata = io.out.r.bits.rdata

  io.out.r.ready     := io.in.r.ready
  io.in.r.bits.rdata := Mux(arByteOrderReg, rdata(63, 32), rdata(31, 0))
  io.in.r.bits.rresp := io.out.r.bits.rresp
  io.in.r.bits.rid   := io.out.r.bits.rid
  io.in.r.valid      := io.out.r.valid
  io.in.r.bits.rlast := io.out.r.bits.rlast

}
