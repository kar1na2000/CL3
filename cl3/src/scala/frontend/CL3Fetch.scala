package cl3

import chisel3._
import chisel3.util._

class FEIO extends Bundle {
  val mem   = new CL3ICacheIO
  val br    = Input(new BrInfo)
  val de    = Decoupled(new FEInfo)
  val bp    = Flipped(new NPCIO)
  val flush = Input(Bool())
}

class CL3Fetch() extends Module with CL3Config {

  val io = IO(new FEIO)

  val outstanding_q = RegInit(false.B)
  when(io.mem.req.fire) {
    outstanding_q := true.B
  }.elsewhen(io.mem.resp.valid) {
    outstanding_q := false.B
  }

  val flush_q = RegInit(false.B)
  flush_q := io.mem.req.bits.invalidate && io.mem.req.valid && !io.mem.req.ready

  val active_q    = RegInit(false.B)
  val mem_is_busy = outstanding_q && !io.mem.resp.valid
  val stall       = !io.de.ready || mem_is_busy || !io.mem.req.ready

  val branch_q      = RegInit(false.B)
  val branch_pc_q   = RegInit(0.U(32.W))
  val branch_priv_q = RegInit(0.U(2.W)) // M mode

  val branch      = Wire(Bool())
  val branch_pc   = Wire(UInt(32.W))
  val branch_priv = Wire(UInt(2.W))

  if (EnableMMU) {

    branch      := branch_q
    branch_pc   := branch_pc_q
    branch_priv := branch_priv_q

    when(io.br.valid) {
      branch_q      := true.B
      branch_pc_q   := io.br.pc
      branch_priv_q := io.br.priv
    }.elsewhen(io.mem.req.fire) {
      branch_q    := false.B
      branch_pc_q := 0.U
    }
  } else {

    branch      := branch_q || io.br.valid
    branch_pc   := Mux(branch_q && !io.br.valid, branch_pc_q, io.br.pc)
    branch_priv := 0.U

    when(io.br.valid && mem_is_busy) {
      branch_q    := branch
      branch_pc_q := branch_pc
    }.elsewhen(!mem_is_busy) {
      branch_q    := false.B
      branch_pc_q := 0.U
    }
  }

  if (EnableMMU) {
    when(branch && !stall) {
      active_q := true.B
    }
  } else {
    when(branch) {
      active_q := true.B
    }
  }

  val stall_q = RegNext(stall, false.B)

  val pc_q        = RegInit(0.U(32.W))
  val last_pc_q   = RegInit(0.U(32.W))
  val last_pred_q = RegInit(0.U(2.W))

  if (EnableMMU) {
    when(branch && !stall) {
      pc_q := branch_pc
    }.elsewhen(!stall) {
      pc_q := io.bp.npc
    }
  } else {
    when(branch && (stall || !active_q || stall_q)) {
      pc_q := branch_pc
    }.elsewhen(!stall) {
      pc_q := io.bp.npc
    }
  }

  val icache_pc   = Wire(UInt(32.W))
  val icache_priv = Wire(UInt(2.W))
  val drop_resp   = Wire(Bool())

  if (EnableMMU) {
    val priv_q        = RegInit(0.U(2.W))
    val last_branch_q = RegInit(false.B)

    when(branch && !stall) {
      priv_q := branch_priv
    }

    when(branch && !stall) {
      last_branch_q := true.B
    }.elsewhen(!stall) {
      last_branch_q := false.B
    }

    icache_pc   := pc_q
    icache_priv := priv_q
    drop_resp   := (branch || last_branch_q)
  } else {
    icache_pc   := Mux(branch && !stall_q, branch_pc, pc_q)
    icache_priv := 0.U
    drop_resp   := branch
  }

  // Last fetch address
  when(io.mem.req.fire) {
    last_pc_q := icache_pc
  }

  when(io.mem.req.fire) {
    last_pred_q := io.bp.taken
  }.elsewhen(io.mem.resp.valid) {
    last_pred_q := 0.U
  }

  // TODO:
  io.mem.req.valid           := active_q && io.de.ready && !mem_is_busy
  io.mem.req.bits.wdata      := 0.U
  io.mem.req.bits.mask       := "b1111".U
  io.mem.req.bits.cacheable  := true.B // TODO:
  io.mem.req.bits.size       := 3.U
  io.mem.req.bits.wen        := false.B
  io.mem.req.bits.addr       := Cat(icache_pc(31, 3), 0.U(3.W))
  io.mem.req.bits.flush      := io.flush || flush_q
  io.mem.req.bits.invalidate := false.B

  val skid_buffer_q = RegInit(0.U.asTypeOf(new FEInfo))
  val skid_valid_q  = RegInit(false.B)

  // TODO: power
  when(io.de.valid && !io.de.ready) {
    skid_valid_q  := true.B
    skid_buffer_q := io.de.bits
  }.otherwise {
    skid_valid_q := false.B
  }

  val fetch_valid = io.mem.resp.valid && !drop_resp
  val fetch_pc    = Cat(last_pc_q(31, 3), 0.U(3.W))

  io.de.valid      := fetch_valid || skid_valid_q
  io.de.bits.pc    := Mux(skid_valid_q, skid_buffer_q.pc, fetch_pc)
  io.de.bits.inst  := Mux(skid_valid_q, skid_buffer_q.inst, io.mem.resp.bits.rdata)
  io.de.bits.pred  := Mux(skid_valid_q, skid_buffer_q.pred, last_pred_q)
  io.de.bits.fault := Mux(skid_valid_q, skid_buffer_q.fault, io.mem.resp.bits.err)

  io.bp.pc     := icache_pc
  io.bp.accept := !stall
}
