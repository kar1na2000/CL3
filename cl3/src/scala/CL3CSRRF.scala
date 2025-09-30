package cl3

import chisel3._
import chisel3.util._

class CSRRFIO() extends Bundle {

  val ren   = Input(Bool()) // Reading CSR also has side effects
  val raddr = Input(UInt(12.W))
  val rdata = Output(UInt(32.W))

  val wen   = Input(Bool())
  val waddr = Input(UInt(12.W))
  val wdata = Input(UInt(32.W))

  val trap = Input(UInt(6.W))

  val pc       = Input(UInt(32.W))
  val inst     = Input(UInt(32.W))
  val bootAddr = Input(UInt(32.W))

  val br = Output(new BrInfo)

  val irq = Output(Bool())

}

class CL3CSRRF() extends Module with CSRConstant {
  val io = IO(new CSRRFIO())

  val csr_waddr = io.waddr
  val csr_raddr = io.raddr
  val csr_wen   = io.wen
  val csr_ren   = io.ren
  val csr_wdat  = io.wdata

  // from debug module
  val csr_update_en = false.B
  val dpc_update    = false.B
  val dbg_cause     = false.B

  val wen_dcsr      = Wire(Bool())
  val wen_dpc       = Wire(Bool())
  val wen_dscratch0 = Wire(Bool())
  val wen_dscratch1 = Wire(Bool())
  val wen_mtvec     = Wire(Bool())
  val wen_mepc      = Wire(Bool())
  val wen_mcause    = Wire(Bool())
  val wen_mscratch  = Wire(Bool())

  class DCSRBundle extends Bundle {
    val xdebugver = UInt(4.W)
    val reserved1 = UInt(12.W)
    val ebreakm   = UInt(1.W)
    val reserved2 = UInt(1.W)
    val ebreaks   = UInt(1.W)
    val ebreaku   = UInt(1.W)
    val stepie    = UInt(1.W)
    val stopcount = UInt(1.W)
    val stoptime  = UInt(1.W)
    val cause     = UInt(3.W)
    val reserved3 = UInt(1.W)
    val mprven    = UInt(1.W)
    val nmip      = UInt(3.W)
    val step      = UInt(1.W)
    val prv       = UInt(2.W)
  }

  val dcsr_wdat = csr_wdat.asTypeOf(new DCSRBundle)

  val xdebugver = WireDefault(4.U(4.W))                          // debug support exists as it is described in this document
  val ebreakm   = RegEnable(dcsr_wdat.ebreakm, 0.U(1.W), wen_dcsr)
  val ebreaks   = RegEnable(dcsr_wdat.ebreaks, 0.U(1.W), wen_dcsr)
  val ebreaku   = RegEnable(dcsr_wdat.ebreaku, 0.U(1.W), wen_dcsr)
  val stepie    = WireDefault(0.U(1.W))                          // Interrupts are disabled during single stepping
  val stopcount = WireDefault(0.U(1.W))                          // Stop counting when the debug module is halted
  val stoptime  = WireDefault(0.U(1.W))                          // Don't incrment any hart-loacal timers while in Debug Mode
  val cause     = RegEnable(dcsr_wdat.cause, 0.U(3.W), wen_dcsr) // debug cause
  val mprven    = WireDefault(0.U(1.W))                          // MPRV in mstatus is ignored
  val nmip      = RegEnable(dcsr_wdat.nmip, 0.U(3.W), wen_dcsr)  // Non-maskable interrupt pending
  val step      = RegEnable(dcsr_wdat.step, 0.U(1.W), wen_dcsr)  // Single step
  val prv       = RegEnable(dcsr_wdat.prv, 0.U(3.W), wen_dcsr)   // privilege level when Debug Mode was entered

//TODO:
  val dcsr = Cat(
    xdebugver,
    0.U(12.W),
    ebreakm,
    0.U(1.W),
    ebreaks,
    ebreaku,
    stepie,
    stopcount,
    stoptime,
    cause,
    0.U(1.W),
    mprven,
    nmip,
    step,
    prv
  )

  // Debug PC (dpc, at 0x7b1)
  val dpc_wdat = Mux1H(
    Seq(
      csr_update_en -> dpc_update,
      wen_dpc       -> csr_wdat
    )
  )
  val dpc      = RegEnable(dpc_wdat, 0.U(32.W), wen_dpc) // Debug PC

  // Debug Scratch (dscratch0, at 0x7b2)
  val dscratch0 = RegEnable(csr_wdat, 0.U(32.W), wen_dscratch0) // Debug Scratch 0

  // Debug Scratch (dscratch1, at 0x7b3)
  val dscratch1 = RegEnable(csr_wdat, 0.U(32.W), wen_dscratch1) // Debug Scratch 1

  val mstatus  = RegInit("h1800".U(32.W))
  val mtvec    = RegEnable(csr_wdat, 0.U(32.W), wen_mtvec)
  val mscratch = RegEnable(csr_wdat, 0.U(32.W), wen_mscratch)

  val mepc_wdata = Mux(io.trap.orR, io.pc, csr_wdat)
  val mepc       = RegEnable(mepc_wdata, 0.U(32.W), wen_mepc)
  val mcause     = RegEnable(io.trap, 0.U(32.W), wen_mcause)
  val mhartid    = RegInit(0.U)

  val allCSRs = Seq(
    MSTATUS_ADDR   -> mstatus,
    MTVEC_ADDR     -> mtvec,
    MSCRATCH_ADDR  -> mscratch,
    MEPC_ADDR      -> mepc,
    MCAUSE_ADDR    -> mcause,
    DCSR_ADDR      -> dcsr,
    DPC_ADDR       -> dpc,
    DSCRATCH0_ADDR -> dscratch0,
    DSCRATCH1_ADDR -> dscratch1,
    MHARTID_ADDR   -> mhartid
  )

  val csr_rselOh = VecInit(allCSRs.map { case (addr, reg) => csr_raddr === addr })
  val csr_wselOh = VecInit(allCSRs.map { case (addr, reg) => csr_waddr === addr })
  val csr_renOh  = csr_rselOh.map(_ && csr_ren)
  val csr_wenOh  = csr_wselOh.map(_ && csr_wen)

  def getCSRIndex(targetAddr: UInt): Int = {
    val index = allCSRs.indexWhere { case (addr, _) =>
      addr.litValue == targetAddr.litValue
    }
    require(index >= 0, s"CSR ${targetAddr.litValue} is undefined")
    index
  }

  def getCSRWen(targetAddr: UInt): Bool = {
    val staticIndex = getCSRIndex(targetAddr)
    csr_wenOh(staticIndex)
  }

  wen_dcsr      := getCSRWen(DCSR_ADDR)
  wen_dpc       := getCSRWen(DPC_ADDR)
  wen_dscratch0 := getCSRWen(DSCRATCH0_ADDR)
  wen_dscratch1 := getCSRWen(DSCRATCH1_ADDR)
  wen_mtvec     := getCSRWen(MTVEC_ADDR)
  wen_mepc      := getCSRWen(MEPC_ADDR) || io.trap.orR
  wen_mcause    := io.trap.orR
  wen_mscratch  := getCSRWen(MSCRATCH_ADDR)

  val csr_regs = allCSRs.map { case (addr, reg) => reg }
  val csr_rdat = Mux1H(csr_renOh, csr_regs)

  io.rdata := csr_rdat

  io.irq := false.B // TODO:

  val reset_q = RegInit(true.B)
  val br_q    = RegInit(0.U.asTypeOf(new BrInfo))

  when(reset_q) {
    reset_q    := false.B
    br_q.pc    := io.bootAddr
    br_q.valid := true.B
  }.otherwise { // TODO:  trap
    br_q.valid := false.B
  }

  br_q.priv := 0.U // TODO:

  io.br := br_q

}
