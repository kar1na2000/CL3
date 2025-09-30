package cl3

import chisel3._
import chisel3.util._

class CL3MMUIO extends Bundle {
  val ctrl     = Input(new MMUCtrlInfo)
  val fetchIn  = Flipped(new CL3ICacheIO)
  val fetchOut = new CL3ICacheIO
  val lsuIn    = Flipped(new CL3DCacheIO)
  val lsuOut   = new CL3DCacheIO
}

class CL3MMU extends Module with CL3Config with MMUConstant {
  val io = IO(new CL3MMUIO())

  if (EnableMMU) {

    val s_idle :: s_level1 :: s_level2 :: s_update :: Nil = Enum(4)
    val state                                             = RegInit(s_idle)
    val is_idle                                           = state === s_idle

    val resp_mmu   = false.B // TODO:
    val resp_valid = resp_mmu && io.lsuOut.resp.valid
    val resp_error = io.lsuOut.resp.bits.err
    val resp_data  = io.lsuOut.resp.bits.rdata

    val load_q = RegInit(false.B)
    when(io.lsuIn.req.valid && !io.lsuIn.req.bits.wen) {
      load_q := !io.lsuIn.req.ready
    }
    val load   = io.lsuIn.req.valid && !io.lsuIn.req.bits.wen || load_q

    val store_q = RegInit(0.U(4.W))
    when(io.lsuIn.req.valid && io.lsuIn.req.bits.wen) {
      store_q := Mux(io.lsuIn.req.ready, 0.U, io.lsuIn.req.bits.mask)
    }
    val store   = io.lsuIn.req.valid && io.lsuIn.req.bits.wen | store_q

    val lsu_in_addr_q = RegEnable(io.lsuIn.req.bits.addr, load || store.orR)
    val lsu_addr      = Mux(load || store.orR, io.lsuIn.req.bits.addr, lsu_in_addr_q)

    // ============== Page Table Walker Logic ==============
    val dtlb_req = Reg(Bool())

    val vm_enable    = io.ctrl.satp(31)
    val ptbr         = Cat(io.ctrl.satp(19, 0), 0.U(12.W))
    val ifetch_vm    = false.B // TODO:
    val dfetch_vm    = io.ctrl.priv =/= 0.U
    val supervisor_i = false.B
    val supervisor_d = io.ctrl.priv === 1.U
    val vm_i_enable  = ifetch_vm
    val vm_d_enable  = vm_enable && dfetch_vm

    val itlb_hit  = Wire(Bool()) // Defined later in TLB section
    val dtlb_hit  = Wire(Bool()) // Defined later in TLB section
    val itlb_miss = io.fetchIn.req.valid && vm_i_enable && !itlb_hit
    val dtlb_miss = (load || store.orR) && vm_d_enable && !dtlb_hit

    // Data miss has higher priority for translation request
    val request_addr = Mux(
      is_idle,
      Mux(dtlb_miss, lsu_addr, io.fetchIn.req.bits.addr),
      Mux(dtlb_req, lsu_addr, io.fetchIn.req.bits.addr)
    )

    val pte_addr  = Reg(UInt(32.W))
    val pte_entry = Reg(UInt(32.W))
    val virt_addr = Reg(UInt(32.W))

    val pte_ppn   = Cat(0.U(10.W), resp_data(31, 10))
    val pte_flags = resp_data(9, 0)

    // Page walk state transitions
    switch(state) {
      is(s_idle) {
        when(itlb_miss || dtlb_miss) {
          pte_addr  := ptbr + Cat(0.U(20.W), request_addr(31, 22), 0.U(2.W))
          virt_addr := request_addr
          dtlb_req  := dtlb_miss
          state     := s_level1
        }
      }
      is(s_level1) {
        when(resp_valid) {
          val pte_is_pointer = !(resp_data(PAGE_READ) || resp_data(PAGE_WRITE) || resp_data(PAGE_EXEC))
          when(resp_error.orR || !resp_data(PAGE_PRESENT)) { // Fault or not present
            pte_entry := 0.U
            state     := s_update
          }.elsewhen(pte_is_pointer) { // Pointer to next level
            pte_addr := Cat(resp_data(29, 10), 0.U(12.W)) + Cat(0.U(20.W), request_addr(21, 12), 0.U(2.W))
            state    := s_level2
          }.otherwise { // Superpage, translation complete
            pte_entry := (pte_ppn | Cat(0.U(22.W), request_addr(21, 12))) << 12.U | Cat(0.U(22.W), pte_flags)
            state     := s_update
          }
        }
      }
      is(s_level2) {
        when(resp_valid) {
          when(resp_data(PAGE_PRESENT)) { // Page, translation complete
            pte_entry := (pte_ppn << 12.U) | Cat(0.U(22.W), pte_flags)
          }.otherwise { // Page fault
            pte_entry := 0.U
          }
          state := s_update
        }
      }
      is(s_update) {
        state := s_idle
      }
    }

    // ============== Instruction TLB (1-entry) ==============
    val itlb_valid   = RegInit(false.B)
    val itlb_va_addr = Reg(UInt(20.W))
    val itlb_entry   = Reg(UInt(32.W))

    when(io.ctrl.flush) {
      itlb_valid := false.B
    }.elsewhen(state === s_update && !dtlb_req) {
      itlb_valid   := (virt_addr(31, 12) === io.fetchIn.req.bits.addr(31, 12)) // Ensure TLB entry is for current PC
      itlb_va_addr := virt_addr(31, 12)
      itlb_entry   := pte_entry
    }.elsewhen(state =/= s_idle && !dtlb_req) {
      itlb_valid := false.B
    }

    itlb_hit := io.fetchIn.req.valid && itlb_valid && (itlb_va_addr === io.fetchIn.req.bits.addr(31, 12))

    // Instruction fetch fault detection
    val pc_fault_r = WireDefault(false.B)
    when(vm_i_enable && itlb_hit) {
      when(supervisor_i) { // Supervisor mode
        pc_fault_r := Mux(itlb_entry(PAGE_USER), true.B, !itlb_entry(PAGE_EXEC))
      }.otherwise { // User mode
        pc_fault_r := !itlb_entry(PAGE_EXEC) || !itlb_entry(PAGE_USER)
      }
    }
    val pc_fault_q = RegNext(pc_fault_r, false.B)

    // Instruction fetch outputs
    io.fetchOut.req.valid     := (~vm_i_enable && io.fetchIn.req.valid) || (itlb_hit && !pc_fault_r)
    io.fetchOut.req.bits.addr := Mux(
      vm_i_enable,
      Cat(itlb_entry(31, 12), io.fetchIn.req.bits.addr(11, 0)),
      io.fetchIn.req.bits.addr
    )
    io.fetchIn.req.ready      := (~vm_i_enable && io.fetchOut.req.ready) || (vm_i_enable && itlb_hit && io.fetchOut.req.ready) || pc_fault_r

    io.fetchIn.resp.valid      := io.fetchOut.resp.valid || pc_fault_q
    io.fetchIn.resp.bits.err   := io.fetchOut.resp.bits.err
    io.fetchIn.resp.bits.rdata := io.fetchOut.resp.bits.rdata

    // ============== Data TLB (1-entry) ==============
    val dtlb_valid   = RegInit(false.B)
    val dtlb_va_addr = Reg(UInt(20.W))
    val dtlb_entry   = Reg(UInt(32.W))

    when(io.ctrl.flush) {
      dtlb_valid := false.B
    }.elsewhen(state === s_update && dtlb_req) {
      dtlb_valid   := true.B
      dtlb_va_addr := virt_addr(31, 12)
      dtlb_entry   := pte_entry
    }

    dtlb_hit := dtlb_valid && (dtlb_va_addr === lsu_addr(31, 12))

    // Data access fault detection
    val load_fault_r = WireDefault(false.B)
    when(vm_d_enable && load && dtlb_hit) {
      when(supervisor_d) { // Supervisor mode
        load_fault_r := Mux(
          dtlb_entry(PAGE_USER) && !io.ctrl.sum,
          true.B,
          !(dtlb_entry(PAGE_READ) || (io.ctrl.mxr && dtlb_entry(PAGE_EXEC)))
        )
      }.otherwise { // User mode
        load_fault_r := !dtlb_entry(PAGE_READ) || !dtlb_entry(PAGE_USER)
      }
    }
    val load_fault_q = RegNext(load_fault_r, false.B)

    val store_fault_r = WireDefault(false.B)
    when(vm_d_enable && store.orR && dtlb_hit) {
      when(supervisor_d) { // Supervisor mode
        store_fault_r := Mux(dtlb_entry(PAGE_USER) && !io.ctrl.sum, true.B, !dtlb_entry(PAGE_WRITE))
      }.otherwise { // User mode
        store_fault_r := !dtlb_entry(PAGE_WRITE) || !dtlb_entry(PAGE_USER)
      }
    }
    val store_fault_q = RegNext(store_fault_r, false.B)

    // Translated LSU signals
    val lsu_out_rd        = Mux(vm_d_enable, load && dtlb_hit && !load_fault_r, !io.lsuIn.req.bits.wen)
    val lsu_out_wr        = Mux(vm_d_enable, store & Fill(4, dtlb_hit && !store_fault_r), io.lsuIn.req.bits.wen)
    val lsu_out_addr      = Mux(vm_d_enable, Cat(dtlb_entry(31, 12), lsu_addr(11, 0)), lsu_addr)
    val lsu_out_cacheable = true.B

    // ============== LSU Bus Muxing (CPU vs. MMU) ==============
    val mem_req_q  = RegInit(false.B)
    val mmu_accept = Wire(Bool())

    when(state === s_idle && (itlb_miss || dtlb_miss)) {
      mem_req_q := true.B
    }.elsewhen(
      state === s_level1 && resp_valid && !resp_error && resp_data(PAGE_PRESENT) && !(resp_data(PAGE_READ) || resp_data(
        PAGE_WRITE
      ) || resp_data(PAGE_EXEC))
    ) {
      mem_req_q := true.B
    }.elsewhen(mmu_accept) {
      mem_req_q := false.B
    }

    // Muxing logic to arbitrate LSU port
    val read_hold   = RegInit(false.B)
    val src_mmu_reg = Reg(Bool())
    val src_mmu     = Mux(read_hold, src_mmu_reg, mem_req_q)

    when(io.lsuOut.req.valid && !io.lsuOut.req.ready) {
      read_hold   := true.B
      src_mmu_reg := src_mmu
    }.elsewhen(io.lsuOut.req.ready) {
      read_hold := false.B
    }

    mmu_accept := src_mmu && io.lsuOut.req.ready
    val cpu_accept = !src_mmu && io.lsuOut.req.ready

    io.lsuOut.req.bits.wen       := Mux(src_mmu, mem_req_q, lsu_out_rd)
    io.lsuOut.req.valid          := Mux(src_mmu, 0.U, mem_req_q)
    io.lsuOut.req.bits.addr      := Mux(src_mmu, pte_addr, lsu_out_addr)
    io.lsuOut.req.bits.wdata     := io.lsuIn.req.bits.wdata
    io.lsuOut.req.bits.cacheable := Mux(src_mmu, true.B, lsu_out_cacheable)

    io.lsuIn.resp.valid      := (io.lsuOut.resp.valid && !resp_mmu) || store_fault_q || load_fault_q
    io.lsuIn.resp.bits.err   := 0.U // TODO:
    io.lsuIn.resp.bits.rdata := io.lsuOut.resp.bits.rdata
    io.lsuIn.req.ready       := (~vm_d_enable && cpu_accept) || (vm_d_enable && dtlb_hit && cpu_accept) || store_fault_r || load_fault_r

  } else {
    io.fetchIn.req <> io.fetchOut.req
    io.fetchIn.resp <> io.fetchOut.resp

    io.lsuIn.req <> io.lsuOut.req
    io.lsuIn.resp <> io.lsuOut.resp

  }
}
