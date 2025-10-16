package cl3

import chisel3._
import chisel3.util._


class CL3IssueIO extends Bundle {
  val in = new Bundle {
    val fetch = Vec(2, Flipped(Decoupled(Input(new DEInfo))))
    val csr   = new ISCSRInput
    val exec  = Vec(2, new ISEXUInput)
    val mul   = new PipeMULInput // TODO: maybe we can use BoringUtil here
    val div   = new PipeDIVInput
    val lsu   = new PipeLSUInput
    val irq   = Input(Bool())
  }

  val out = new Bundle {
    val br   = Output(new BrInfo)
    val bp   = Output(new BpInfo)
    val op   = Vec(6, Output(new OpInfo))
    val csr  = new ISCSROutput
    val hold = Output(Bool())
  }
}

class CL3Issue extends Module with CL3Config {
  val io = IO(new CL3IssueIO)

  val fetch0 = io.in.fetch(0)
  val fetch1 = io.in.fetch(1)

  val pc_q   = RegInit(0.U(32.W))
  val priv_q = RegInit("b11".U(2.W))

  val single_issue = Wire(Bool())
  val dual_issue   = Wire(Bool())
  val flush        = Wire(Bool())
  val stall        = Wire(Bool())

// Pay attention to the branch priority
  when(io.in.csr.br.valid) {
    pc_q   := io.in.csr.br.pc
    priv_q := io.in.csr.br.priv
  }.elsewhen(io.in.exec(1).br.valid) {
    pc_q := io.in.exec(1).br.pc
  }.elsewhen(io.in.exec(0).br.valid) {
    pc_q := io.in.exec(0).br.pc
  }.elsewhen(dual_issue) {
    pc_q := pc_q + 8.U
  }.elsewhen(single_issue) {
    pc_q := pc_q + 4.U
  }

  dontTouch(io)

  val fetch0_pc_match = fetch0.bits.pc(31, 1) === (pc_q(31, 1))
  val fetch1_pc_match = fetch1.bits.pc(31, 1) === (pc_q(31, 1))

  val fetch0_ok = fetch0.valid && fetch0_pc_match && !(flush || io.in.csr.br.valid)
  val fetch1_ok = fetch1.valid && (fetch1_pc_match || fetch0_pc_match) && !(flush || io.in.csr.br.valid)

  val mispred = (fetch0.valid || fetch1.valid) && !(fetch0_ok || fetch1_ok)

  val slot = Wire(Vec(2, Valid(new DEInfo)))

  slot(0).bits  := Mux(fetch0_ok, fetch0.bits, fetch1.bits)
  slot(0).valid := Mux(fetch0_ok, true.B, Mux(fetch1_ok, true.B, false.B))

  slot(1).bits  := fetch1.bits
  slot(1).valid := Mux(fetch0_ok, fetch1_ok, false.B)

  val pipe0 = Module(new CL3Pipe())
  val pipe1 = Module(new CL3Pipe())

  stall := pipe0.io.out.stall || pipe1.io.out.stall
  flush := pipe0.io.out.flush || pipe1.io.out.flush

  io.out.hold := stall

  // TODO: This manual wiring for each pipeline is verbose and error-prone.
  //       It should be refactored for better scalability and maintainability.
  pipe0.io.in.stall        := stall
  pipe0.io.in.flush        := pipe1.io.out.flush
  pipe0.io.in.flushWB      := false.B // TODO:
  pipe0.io.in.irq          := io.in.irq
  pipe0.io.in.exu.br       := io.in.exec(0).br
  pipe0.io.in.exu.result   := io.in.exec(0).result
  pipe0.io.in.csr          := io.in.csr
  pipe0.io.in.lsu          := io.in.lsu
  pipe0.io.in.mul          := io.in.mul
  pipe0.io.in.div          := io.in.div
  pipe0.io.in.issue.info   := slot(0).bits
  pipe0.io.in.issue.except := 0.U     // TODO:

  pipe1.io.in.stall        := stall
  pipe1.io.in.flush        := pipe0.io.out.flush
  pipe1.io.in.flushWB      := pipe0.io.out.flush
  pipe1.io.in.irq          := io.in.irq
  pipe1.io.in.exu.br       := io.in.exec(1).br
  pipe1.io.in.exu.result   := io.in.exec(1).result
  pipe1.io.in.csr          := io.in.csr
  pipe1.io.in.lsu          := io.in.lsu
  pipe1.io.in.mul          := io.in.mul
  pipe1.io.in.div          := io.in.div
  pipe1.io.in.issue.info   := slot(1).bits
  pipe1.io.in.issue.except := 0.U // TODO:

  val rf = Module(new CL3RF())

  val bypass = Module(new BypassNetwork)

  bypass.io.in.issue(0).raIdx := slot(0).bits.raIdx
  bypass.io.in.issue(0).ra    := rf.io.rd(0).rdata

  bypass.io.in.issue(0).rbIdx := slot(0).bits.rbIdx
  bypass.io.in.issue(0).rb    := rf.io.rd(1).rdata

  bypass.io.in.issue(1).raIdx := slot(1).bits.raIdx
  bypass.io.in.issue(1).ra    := rf.io.rd(2).rdata

  bypass.io.in.issue(1).rbIdx := slot(1).bits.rbIdx
  bypass.io.in.issue(1).rb    := rf.io.rd(3).rdata

  bypass.io.in.pipe(0) := pipe0.io.out
  bypass.io.in.pipe(1) := pipe1.io.out

  rf.io.rd(0).raddr := slot(0).bits.raIdx
  rf.io.rd(1).raddr := slot(0).bits.rbIdx

  rf.io.rd(2).raddr := slot(1).bits.raIdx
  rf.io.rd(3).raddr := slot(1).bits.rbIdx

  val slot_op = Wire(Vec(2, new OpInfo))

  slot_op(0)       := OpInfo.fromDE(slot(0).bits)
  slot_op(0).valid := slot(0).valid
  slot_op(0).ra    := bypass.io.out.ra0
  slot_op(0).rb    := bypass.io.out.rb0

  pipe0.io.in.issue.ra := slot_op(0).ra
  pipe0.io.in.issue.rb := slot_op(0).rb

  slot_op(1)       := OpInfo.fromDE(slot(1).bits)
  slot_op(1).valid := slot(1).valid
  slot_op(1).ra    := bypass.io.out.ra1
  slot_op(1).rb    := bypass.io.out.rb1

  pipe1.io.in.issue.ra := slot_op(1).ra
  pipe1.io.in.issue.rb := slot_op(1).rb

  val pipe_e1 = Wire(Vec(2, new PipeInfo))
  pipe_e1(0) := pipe0.io.out.e1
  pipe_e1(1) := pipe1.io.out.e1

  val div_pending = Wire(Bool())

  /*---------------- slot 0 issue check ---------------- */

  /* stall the instruction issue if it has a data dependency
  on a multi-cycle instruction currently in the E1 stage. */
  val slot0_data_check =
    pipe_e1(0).hazard_detect(slot_op(0).raIdx) ||
      pipe_e1(0).hazard_detect(slot_op(0).rbIdx) ||
      pipe_e1(1).hazard_detect(slot_op(0).raIdx) ||
      pipe_e1(1).hazard_detect(slot_op(0).rbIdx)

  val slot0_struct_check =
    io.in.lsu.stall || div_pending || stall

  val slot0_order_check =
    (pipe_e1(0).isMem || pipe_e1(1).isMem) &&
      (slot(0).bits.isDIV || slot(0).bits.isMUL || slot(0).bits.isCSR) &&
      slot(0).valid

  val slot0_fire = !(slot0_data_check || slot0_struct_check || slot0_order_check || io.in.irq) && slot_op(0).valid

  /*---------------- slot 1 issue check ---------------- */

  val slot1_data_check =
    pipe_e1(0).hazard_detect(slot_op(1).raIdx) ||
      pipe_e1(0).hazard_detect(slot_op(1).rbIdx) ||
      pipe_e1(1).hazard_detect(slot_op(1).raIdx) ||
      pipe_e1(1).hazard_detect(slot_op(1).rbIdx) ||
      slot_op(1).raIdx === slot_op(0).rdIdx && slot_op(0).wen ||
      slot_op(1).rbIdx === slot_op(0).rdIdx && slot_op(0).wen

  val slot1_struct_check =
    io.in.lsu.stall || div_pending || stall || !slot0_fire

  val slot1_order_check =
    (pipe_e1(0).isMem || pipe_e1(1).isMem) &&
      (slot(1).bits.isDIV || slot(1).bits.isMUL || slot(1).bits.isCSR) &&
      slot(1).valid

  val slot1_type_check =
    !(slot(1).bits.isEXU && (slot(0).bits.isEXU || slot(0).bits.isLSU || slot(0).bits.isMUL) ||
      slot(1).bits.isBr && (slot(0).bits.isEXU || slot(0).bits.isLSU || slot(0).bits.isMUL) ||
      slot(1).bits.isLSU && (slot(0).bits.isEXU || slot(0).bits.isMUL) ||
      slot(0).bits.isMUL && (slot(0).bits.isEXU || slot(0).bits.isLSU))

  val slot1_fire =
    !(slot1_data_check || slot1_struct_check || slot1_type_check || slot1_order_check || io.in.irq) && slot_op(1).valid

  pipe0.io.in.issue.fire := slot0_fire
  pipe1.io.in.issue.fire := slot1_fire

// TODO: CSR
  rf.io.wr(0).wen   := pipe0.io.out.wb.wen && pipe0.io.out.wb.commit
  rf.io.wr(0).waddr := pipe0.io.out.wb.rdIdx
  rf.io.wr(0).wdata := pipe0.io.out.wb.result

  rf.io.wr(1).wen   := pipe1.io.out.wb.wen && pipe1.io.out.wb.commit
  rf.io.wr(1).waddr := pipe1.io.out.wb.rdIdx
  rf.io.wr(1).wdata := pipe1.io.out.wb.result

  // EXU 0
  io.out.op(0)       := slot_op(0)
  io.out.op(0).valid := pipe0.io.in.issue.fire

  // EXU 1
  io.out.op(1)       := slot_op(1)
  io.out.op(1).valid := pipe1.io.in.issue.fire

  // LSU
  val slot0_issue_lsu = slot(0).bits.isLSU && slot0_fire
  val slot1_issue_lsu = slot(1).bits.isLSU && slot1_fire

  val lsu_op = Mux(slot0_issue_lsu, slot_op(0), slot_op(1))
  io.out.op(2)       := lsu_op
  io.out.op(2).valid := lsu_op.valid && ~io.in.irq && (slot0_issue_lsu || slot1_issue_lsu)

  // MUL
  val slot0_issue_mul = slot(0).bits.isMUL && slot0_fire
  val slot1_issue_mul = slot(1).bits.isMUL && slot1_fire

  val mul_op = Mux(slot0_issue_mul, slot_op(0), slot_op(1))
  io.out.op(3)       := mul_op
  io.out.op(3).valid := mul_op.valid && ~io.in.irq && (slot0_issue_mul || slot1_issue_mul)

  // DIV
  io.out.op(4)       := slot_op(0)
  io.out.op(4).valid := pipe0.io.in.issue.fire && slot(0).bits.isDIV

  val div_pending_q = RegInit(false.B)
  when(pipe0.io.in.flush || pipe1.io.in.flush) {
    div_pending_q := false.B
  }.elsewhen(io.out.op(4).valid) {
    div_pending_q := true.B
  }.elsewhen(io.in.div.valid) {
    div_pending_q := false.B
  }
  div_pending := div_pending_q

  // CSR
  io.out.op(5)       := slot_op(0)
  io.out.op(5).valid := slot_op(0).valid && ~io.in.irq && slot(0).bits.isCSR

  dual_issue   := pipe1.io.in.issue.fire && !io.in.irq
  single_issue := pipe0.io.in.issue.fire && !dual_issue && !io.in.irq

  // Branch
  io.out.br.valid := mispred || io.in.csr.br.valid
  io.out.br.pc    := Mux(io.in.csr.br.valid, io.in.csr.br.pc, pc_q)
  io.out.br.priv  := Mux(io.in.csr.br.valid, io.in.csr.br.priv, priv_q)

  io.out.bp       := Mux(pipe1.io.out.e1.isBr, io.in.exec(1).bp, io.in.exec(0).bp)
  io.out.bp.valid := mispred

  io.out.csr.waddr  := pipe0.io.out.wb.csr.waddr
  io.out.csr.wdata  := pipe0.io.out.wb.csr.wdata
  io.out.csr.wen    := pipe0.io.out.wb.csr.wen
  io.out.csr.except := 0.U // TODO:

  // handshake

  io.in.fetch(0).ready := (fetch0_ok && pipe0.io.in.issue.fire) && !io.in.irq
  io.in.fetch(1).ready := (fetch1_ok && !fetch0_ok && pipe0.io.in.issue.fire || pipe1.io.in.issue.fire) && !io.in.irq

  if (EnableDiff) {
    val difftest = Module(new Difftest)
    difftest.io.reset := reset
    difftest.io.clock := clock

    // TODO: use a more elegant way to do signal connection
    difftest.io.diff_info(0).commit := pipe0.io.out.wb.commit
    difftest.io.diff_info(0).pc     := pipe0.io.out.wb.pc
    difftest.io.diff_info(0).inst   := pipe0.io.out.wb.inst
    difftest.io.diff_info(0).skip   := false.B
    difftest.io.diff_info(0).npc    := pipe0.io.out.wb.npc
    difftest.io.diff_info(0).rdIdx  := pipe0.io.out.wb.rdIdx
    difftest.io.diff_info(0).wen    := pipe0.io.out.wb.wen
    difftest.io.diff_info(0).wdata  := pipe0.io.out.wb.result

    difftest.io.diff_info(1).commit := pipe1.io.out.wb.commit
    difftest.io.diff_info(1).pc     := pipe1.io.out.wb.pc
    difftest.io.diff_info(1).inst   := pipe1.io.out.wb.inst
    difftest.io.diff_info(1).skip   := false.B
    difftest.io.diff_info(1).npc    := pipe1.io.out.wb.npc
    difftest.io.diff_info(1).rdIdx  := pipe1.io.out.wb.rdIdx
    difftest.io.diff_info(1).wen    := pipe1.io.out.wb.wen
    difftest.io.diff_info(1).wdata  := pipe1.io.out.wb.result
  }

}
