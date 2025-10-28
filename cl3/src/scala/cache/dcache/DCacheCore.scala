package cl3

import chisel3._
import chisel3.util._

class dcache_core(p: DCacheParams) extends Module {
    val io = IO(new DCacheCoreIO(p))

    val (cpureq, cpuresp, pmemreq, pmemresp) = (io.cpu.req, io.cpu.resp, io.pmem.req, io.pmem.resp)
    val amo = io.amo

    val states = Enum(12)

    val STATE_RESET      = states(0)
    val STATE_FLUSH_ADDR = states(1)
    val STATE_FLUSH      = states(2)
    val STATE_LOOKUP     = states(3)
    val STATE_READ       = states(4)
    val STATE_WRITE      = states(5)
    val STATE_REFILL     = states(6)
    val STATE_EVICT      = states(7)
    val STATE_EVICT_WAIT = states(8)
    val STATE_INVALIDATE = states(9)
    val STATE_WRITEBACK  = states(10)
    val STATE_AMOCOMPUTE = states(11)


    val state_q = RegInit(STATE_RESET)
    val next_state_r = WireDefault(state_q)
//-----------------------------------------------------------------
// Request buffer
//-----------------------------------------------------------------
    val mem_addr_m_q      = RegInit(0.U(p.addrW.W))
    val mem_data_m_q      = RegInit(0.U(p.dataW.W))
    val mem_wr_m_q        = RegInit(0.U(p.wstrbW.W))
    val mem_rd_m_q        = RegInit(false.B)
    val mem_tag_m_q       = RegInit(0.U(p.reqTagW.W))  
    val mem_inval_m_q     = RegInit(false.B) 
    val mem_writeback_m_q = RegInit(false.B) 
    val mem_flush_m_q     = RegInit(false.B)
    val mem_amo_m_q       = RegInit(false.B)
    val mem_amoop_m_q     = RegInit(amoOp.AMOSWAP)
    val mem_lr_m_q        = RegInit(false.B)
    val mem_sc_m_q        = RegInit(false.B)
    
    when(io.cpu.accept && cpureq.rd) {
        mem_addr_m_q      := cpureq.addr
        mem_data_m_q      := cpureq.dataWr
        mem_wr_m_q        := cpureq.wr
        mem_rd_m_q        := cpureq.rd
        mem_tag_m_q       := cpureq.reqTag
        mem_inval_m_q     := cpureq.invalidate
        mem_writeback_m_q := cpureq.writeback
        mem_flush_m_q     := cpureq.flush
        mem_amo_m_q       := amo.isAmo
        mem_amoop_m_q     := amo.amoCode
        mem_lr_m_q        := amo.isLR
        mem_sc_m_q        := amo.isSC

    } 
    .elsewhen(cpuresp.ack) {
        mem_addr_m_q      := 0.U
        mem_data_m_q      := 0.U
        mem_wr_m_q        := 0.U
        mem_rd_m_q        := false.B
        mem_tag_m_q       := 0.U
        mem_inval_m_q     := false.B
        mem_writeback_m_q := false.B
        mem_flush_m_q     := false.B
        mem_amo_m_q       := false.B
        mem_amoop_m_q     := amoOp.AMOSWAP
        mem_lr_m_q        := false.B
        mem_sc_m_q        := false.B
    }

    val mem_accept_r = WireDefault(false.B)
    val tag_hit_any_m_w = Wire(Bool())

    when(state_q === STATE_LOOKUP){
        // Previous access missed - do not accept new requests
        when ((mem_rd_m_q || mem_amo_m_q || (mem_wr_m_q =/= 0.U)) && !tag_hit_any_m_w){
            mem_accept_r := false.B
        // Write followed by read - detect writes to the same bank, or addresses which alias in tag lookups
        } .elsewhen((mem_wr_m_q.orR) && cpureq.rd && cpureq.addr(3, 2) === mem_addr_m_q(3, 2) && mem_addr_m_q(31, 28) =/= 0.U){
            mem_accept_r := false.B
        } .otherwise{
            mem_accept_r := true.B
        }
    }

    io.cpu.accept := mem_accept_r

// Tag comparison address
    val req_addr_tag_cmp_m_w = mem_addr_m_q(p.tagCmpAddrH, p.tagCmpAddrL)

    cpuresp.respTag := mem_tag_m_q

//-----------------------------------------------------------------
// Registers / Wires
//-----------------------------------------------------------------
    val replace_way_q = RegInit(0.U(1.W))
    val pmem_wr_w         = Wire(UInt(4.W)) 
    val pmem_rd_w         = Wire(Bool())
    val pmem_len_w        = Wire(UInt(p.lenW.W)) 
    val pmem_last_w       = Wire(Bool()) 
    val pmem_addr_w       = Wire(UInt(p.addrW.W)) 
    val pmem_write_data_w = Wire(UInt(p.dataW.W)) 
    val pmem_accept_w     = Wire(Bool()) 
    val pmem_ack_w        = Wire(Bool()) 
    val pmem_error_w      = Wire(Bool())   
    val pmem_read_data_w  = Wire(UInt(p.dataW.W))    
    val evict_way_w           = Wire(Bool())
    val tag_dirty_any_m_w     = Wire(Bool())    
    val tag_hit_and_dirty_m_w = Wire(Bool())        
    val flushing_q = RegInit(false.B)

    val flush_addr_q = RegInit(0.U(p.tagReqLineW.W))

    val tag_addr_x_r = Wire(UInt(p.tagReqLineW.W))
    val tag_addr_m_r = Wire(UInt(p.tagReqLineW.W))

    // Read Port
    tag_addr_x_r := cpureq.addr(p.tagReqLineH, p.tagReqLineL)

    // Lookup
    when (state_q === STATE_LOOKUP && (next_state_r === STATE_LOOKUP || next_state_r === STATE_WRITEBACK)){
        tag_addr_x_r := cpureq.addr(p.tagReqLineH, p.tagReqLineL)
    // Cache flush
    } .elsewhen (flushing_q){
        tag_addr_x_r := flush_addr_q
    } .otherwise {
        tag_addr_x_r := mem_addr_m_q(p.tagReqLineH, p.tagReqLineL)
    }

    // Write Port
    tag_addr_m_r := flush_addr_q

    // Cache flush
    when(flushing_q || state_q === STATE_RESET){
        tag_addr_m_r := flush_addr_q
    } .otherwise{
        tag_addr_m_r := mem_addr_m_q(p.tagReqLineH, p.tagReqLineL)
    }

    // Tag RAM write data
    val tag_data_in_m_r = Wire(UInt(p.tagEntryW.W))
    tag_data_in_m_r := 0.U

    // Cache flush
    when(state_q === STATE_FLUSH || state_q === STATE_RESET || flushing_q) {
        tag_data_in_m_r := 0.U
    // Line refill
    } .elsewhen(state_q === STATE_REFILL){
        tag_data_in_m_r := Cat(1.U(1.W), 0.U(1.W), mem_addr_m_q(p.tagCmpAddrH, p.tagCmpAddrL))
    // Invalidate - mark entry (if matching line) not valid (even if dirty...)
    } .elsewhen(state_q === STATE_INVALIDATE){
        tag_data_in_m_r := Cat(0.U(1.W), 0.U(1.W), mem_addr_m_q(p.tagCmpAddrH, p.tagCmpAddrL))
    // Evict completion
    } .elsewhen(state_q === STATE_EVICT_WAIT){
        tag_data_in_m_r := Cat(1.U(1.W), 0.U(1.W), mem_addr_m_q(p.tagCmpAddrH, p.tagCmpAddrL))
    // Write - mark entry as dirty
    } .elsewhen(state_q === STATE_WRITE || (state_q === STATE_LOOKUP && ((mem_wr_m_q.orR) || mem_amo_m_q))){
        tag_data_in_m_r := Cat(1.U(1.W), 1.U(1.W), mem_addr_m_q(p.tagCmpAddrH, p.tagCmpAddrL))
    }
// Tag RAM write enable
    // val tag0_data_out_w = Wire(UInt(p.tagDataW.W))
    // val tag1_data_out_w = Wire(UInt(p.tagDataW.W))
    val tag_data_out_w = Wire(Vec(p.ways, UInt(p.tagDataW.W)))
    dontTouch(tag_data_out_w)
    val tag0_valid_m_w    = tag_data_out_w(0)(p.tagValidBit)
    val tag0_dirty_m_w    = tag_data_out_w(0)(p.tagDirtyBit)
    val tag0_addr_bits_m_w = tag_data_out_w(0)(p.cacheTagAddrBits - 1, 0)

    val tag1_valid_m_w    = tag_data_out_w(1)(p.tagValidBit)
    val tag1_dirty_m_w    = tag_data_out_w(1)(p.tagDirtyBit)
    val tag1_addr_bits_m_w = tag_data_out_w(1)(p.cacheTagAddrBits - 1, 0)

    val tag0_hit_m_w    = tag0_valid_m_w && (tag0_addr_bits_m_w === req_addr_tag_cmp_m_w)
    val tag1_hit_m_w    = tag1_valid_m_w && (tag1_addr_bits_m_w === req_addr_tag_cmp_m_w)
    val tag_hit_m_w     = Seq(tag0_hit_m_w, tag1_hit_m_w)
    val u_tag = Seq.fill(p.ways)(Module(new dcache_core_tag_ram(p)))

    tag_hit_any_m_w       := (tag0_hit_m_w || tag1_hit_m_w)
    tag_hit_and_dirty_m_w := (tag0_hit_m_w & tag0_dirty_m_w) | (tag1_hit_m_w & tag1_dirty_m_w)
    tag_dirty_any_m_w     := (tag0_valid_m_w & tag0_dirty_m_w) | (tag1_valid_m_w & tag1_dirty_m_w)
    
    for (i <- 0 until p.ways) {
        val tag_write_m_r = Wire(Bool())

        tag_write_m_r := false.B

        // Cache flush (reset)
        when (state_q === STATE_RESET) {
            tag_write_m_r := true.B
        }
        // Cache flush
        .elsewhen (state_q === STATE_FLUSH) {
            tag_write_m_r := !tag_dirty_any_m_w
        }
        // Write - hit, mark as dirty
        .elsewhen (state_q === STATE_LOOKUP && (mem_wr_m_q.orR)) {
            tag_write_m_r := tag_hit_m_w(i)
        }
        // Write - write after refill
        .elsewhen (state_q === STATE_WRITE) {
            tag_write_m_r := (replace_way_q === i.U)
        }
        // Write - mark entry as dirty
        .elsewhen (state_q === STATE_EVICT_WAIT && pmem_ack_w) {
            tag_write_m_r := (replace_way_q === i.U)
        }
        // Line refill
        .elsewhen (state_q === STATE_REFILL) {
            tag_write_m_r := (pmem_ack_w && pmem_last_w && (replace_way_q === i.U))
        }
        // Invalidate - line matches address - invalidate
        .elsewhen (state_q === STATE_INVALIDATE) {
            tag_write_m_r := tag_hit_m_w(i)
        }
        u_tag(i).io.p0.addr := tag_addr_x_r
        tag_data_out_w(i)   := u_tag(i).io.p0.rdata
        u_tag(i).io.p1.addr := tag_addr_m_r
        u_tag(i).io.p1.wdata := tag_data_in_m_r
        u_tag(i).io.p1.wen := tag_write_m_r
    }

    val data0_data_out_m_w = Wire(UInt(32.W))
    val data1_data_out_m_w = Wire(UInt(32.W))
    val EVICT_ADDR_W = 32 - p.offBits
    val evict_way_r  = Wire(Bool())
    val evict_data_r = Wire(UInt(32.W))
    val evict_addr_r = Wire(UInt(EVICT_ADDR_W.W))
    evict_way_r  := false.B
    evict_addr_r := Mux(
        flushing_q,
        Cat(tag0_addr_bits_m_w, flush_addr_q),
        Cat(tag0_addr_bits_m_w, mem_addr_m_q(p.tagReqLineH, p.tagReqLineL))
    )
    evict_data_r := data0_data_out_m_w

    switch(replace_way_q) {
        is(0.U(1.W)) {
            evict_way_r  := (tag0_valid_m_w && tag0_dirty_m_w)
            evict_addr_r := Mux(
            flushing_q,
                Cat(tag0_addr_bits_m_w, flush_addr_q),
                Cat(tag0_addr_bits_m_w, mem_addr_m_q(p.tagReqLineH, p.tagReqLineL))
            )
            evict_data_r := data0_data_out_m_w
        }
        is(1.U(1.W)) {
            evict_way_r  := (tag1_valid_m_w && tag1_dirty_m_w)
            evict_addr_r := Mux(
            flushing_q,
                Cat(tag1_addr_bits_m_w, flush_addr_q),
                Cat(tag1_addr_bits_m_w, mem_addr_m_q(p.tagReqLineH, p.tagReqLineL))
            )
            evict_data_r := data1_data_out_m_w
        }
    }

    evict_way_w  := (flushing_q || !tag_hit_any_m_w) && evict_way_r
    val evict_addr_w = evict_addr_r
    val evict_data_w = evict_data_r

//-----------------------------------------------------------------
// DATA RAMS
//-----------------------------------------------------------------
// Data addressing   

    val CACHE_DATA_ADDR_W = p.offBits + p.idxBits - 2
    
    val data_addr_x_r    = Wire(UInt(CACHE_DATA_ADDR_W.W))
    val data_addr_m_r    = Wire(UInt(CACHE_DATA_ADDR_W.W))
    val atoData          = RegInit(0.U(p.dataW.W)) // amo

    val data_write_addr_q = RegInit(0.U(CACHE_DATA_ADDR_W.W))
    // Data RAM refill write address
    when (state_q =/= STATE_REFILL && next_state_r === STATE_REFILL) {
        data_write_addr_q := pmem_addr_w(CACHE_DATA_ADDR_W + 1, 2)
    }.elsewhen (state_q =/= STATE_EVICT && next_state_r === STATE_EVICT) {
        data_write_addr_q := data_addr_m_r + 1.U
    }.elsewhen (state_q === STATE_REFILL && pmem_ack_w) {
        data_write_addr_q := data_write_addr_q + 1.U
    }.elsewhen (state_q === STATE_EVICT && pmem_accept_w) {
        data_write_addr_q := data_write_addr_q + 1.U
    }

// -------------------------------
// // Data RAM address
    data_addr_x_r := cpureq.addr(CACHE_DATA_ADDR_W + 1, 2)
    data_addr_m_r := mem_addr_m_q(CACHE_DATA_ADDR_W + 1, 2)

    // Line refill / evict
    when (state_q === STATE_REFILL || state_q === STATE_EVICT) {
        data_addr_x_r := data_write_addr_q
        data_addr_m_r := data_addr_x_r
    }.elsewhen (state_q === STATE_FLUSH || state_q === STATE_RESET) {
        data_addr_x_r := Cat(flush_addr_q, 0.U((p.offBits - 2).W))
        data_addr_m_r := data_addr_x_r
    }.elsewhen (state_q =/= STATE_EVICT && next_state_r === STATE_EVICT) {
        data_addr_x_r := Cat(mem_addr_m_q(p.tagReqLineH, p.tagReqLineL), 0.U((p.offBits - 2).W))
        data_addr_m_r := data_addr_x_r
    }
    // Lookup post refill
    .elsewhen (state_q === STATE_READ) {
        data_addr_x_r := mem_addr_m_q(CACHE_DATA_ADDR_W + 1, 2)
    }
    // Possible line update on write
    .otherwise {
        data_addr_m_r := mem_addr_m_q(CACHE_DATA_ADDR_W + 1, 2)
    }
    // -------------------------------
// Data RAM write enable (way 0)
// reg [3:0] data0_write_m_r;
    val data0_write_m_r = Wire(UInt(4.W))
    val data1_write_m_r = Wire(UInt(4.W))
    dontTouch(data0_write_m_r)
    dontTouch(data1_write_m_r)

    data0_write_m_r := 0.U

    when (state_q === STATE_REFILL) {
        data0_write_m_r := Mux(pmem_ack_w && (replace_way_q === 0.U), "b1111".U(4.W), "b0000".U(4.W))
    } .elsewhen (state_q === STATE_WRITE || state_q === STATE_LOOKUP) {
        data0_write_m_r := Mux(mem_amo_m_q, "b1111".U(4.W), mem_wr_m_q) & Fill(4, tag_hit_m_w(0))

    }

    val data0_data_in_m_w  = Wire(UInt(32.W))
    data0_data_in_m_w := Mux(state_q === STATE_REFILL, pmem_read_data_w, 
                        Mux(mem_amo_m_q, atoData, mem_data_m_q))

    val u_data0 = Module(new dcache_core_data_ram(p))
    // Read
    u_data0.io.p0.addr := data_addr_x_r
    u_data0.io.p0.wdata := 0.U(32.W)
    u_data0.io.p0.wstrb   := 0.U(4.W)
    data0_data_out_m_w := u_data0.io.p0.rdata
    // Write
    u_data0.io.p1.addr := data_addr_m_r
    u_data0.io.p1.wdata := data0_data_in_m_w
    u_data0.io.p1.wstrb   := data0_write_m_r

    // -------------------------------
    // Data RAM write enable (way 1)
    // reg [3:0] data1_write_m_r;
    
    data1_write_m_r := 0.U
    when (state_q === STATE_REFILL) {
        data1_write_m_r := Mux(pmem_ack_w && (replace_way_q === 1.U), "b1111".U(4.W), "b0000".U(4.W))
    } .elsewhen (state_q === STATE_WRITE || state_q === STATE_LOOKUP) {
        data1_write_m_r := Mux(mem_amo_m_q, "b1111".U(4.W), mem_wr_m_q) & Fill(4, tag_hit_m_w(1))

    }

    val data1_data_in_m_w  = Wire(UInt(32.W))
    data1_data_in_m_w := Mux(state_q === STATE_REFILL, pmem_read_data_w, 
                        Mux(mem_amo_m_q, atoData, mem_data_m_q))

    val u_data1 = Module(new dcache_core_data_ram(p))
    // Read
    u_data1.io.p0.addr := data_addr_x_r
    u_data1.io.p0.wdata := 0.U(32.W)
    u_data1.io.p0.wstrb := 0.U(4.W)
    data1_data_out_m_w := u_data1.io.p0.rdata
    // Write
    u_data1.io.p1.addr := data_addr_m_r
    u_data1.io.p1.wdata := data1_data_in_m_w
    u_data1.io.p1.wstrb := data1_write_m_r

//-----------------------------------------------------------------
// Flush counter
//-----------------------------------------------------------------
    when((state_q === STATE_RESET) || (state_q === STATE_FLUSH && next_state_r === STATE_FLUSH_ADDR)) {
        flush_addr_q := flush_addr_q + 1.U
    }.elsewhen(state_q === STATE_LOOKUP) {
        flush_addr_q := 0.U
    }

    when(state_q === STATE_LOOKUP && next_state_r === STATE_FLUSH_ADDR) {
        flushing_q := true.B
    }.elsewhen(state_q === STATE_FLUSH && next_state_r === STATE_LOOKUP) {
        flushing_q := false.B
    }

    val flush_last_q = RegInit(false.B)

    when(state_q === STATE_LOOKUP) {
        flush_last_q := false.B
    }.elsewhen(flush_addr_q === Fill(p.idxBits, 1.U(1.W))) {
        flush_last_q := true.B
    }

//-----------------------------------------------------------------
// Replacement Policy
//----------------------------------------------------------------- 
// Using random replacement policy - this way we cycle through the ways
// when needing to replace a line.
    when(state_q === STATE_WRITE || state_q === STATE_READ) {
        replace_way_q := replace_way_q + 1.U
    }.elsewhen(flushing_q && tag_dirty_any_m_w && !evict_way_w && state_q =/= STATE_FLUSH_ADDR) {
        replace_way_q := replace_way_q + 1.U
    }.elsewhen(state_q === STATE_EVICT_WAIT && next_state_r === STATE_FLUSH_ADDR) {
        replace_way_q := 0.U
    }.elsewhen(state_q === STATE_FLUSH && next_state_r === STATE_LOOKUP) {
        replace_way_q := 0.U
    }.elsewhen(state_q === STATE_LOOKUP && next_state_r === STATE_FLUSH_ADDR) {
        replace_way_q := 0.U
    }.elsewhen(state_q === STATE_WRITEBACK) {
        when (tag_hit_m_w(0)) {
            replace_way_q := 0.U
        }.elsewhen(tag_hit_m_w(1)) {
            replace_way_q := 1.U
        }
    }

//-----------------------------------------------------------------
// Reservation RAM
//-----------------------------------------------------------------
    val reservedValid = RegInit(false.B)
    val reservedAddr  = RegInit(0.U((p.dataW - 2).W))
    reservedValid := Mux(mem_lr_m_q, true.B, 
                     Mux(mem_sc_m_q, false.B, reservedValid))
    reservedAddr  := Mux(mem_lr_m_q, cpureq.addr(p.dataW - 1, 2), reservedAddr)

//-----------------------------------------------------------------
// Store Condition (SC.W) 
//-----------------------------------------------------------------
    amo.scSuccess := mem_sc_m_q && reservedValid && reservedAddr === cpureq.addr(p.dataW - 1, 2)


//-----------------------------------------------------------------
// Output Result
//-----------------------------------------------------------------
// Data output mux
    val data_r  = Wire(UInt(32.W))
    data_r := data0_data_out_m_w
    when (tag_hit_m_w(0)) {
        data_r := data0_data_out_m_w
    }.elsewhen(tag_hit_m_w(1)) {
        data_r := data1_data_out_m_w
    }

    cpuresp.dataRd := data_r

//-----------------------------------------------------------------
// Atomic calculate
//-----------------------------------------------------------------
    // mem_data_m_q : rs2
    atoData := MuxLookup(mem_amoop_m_q, 0.U(p.dataW.W))(Seq(
            amoOp.AMOADD   -> (mem_data_m_q + data_r),
            amoOp.AMOAND   -> (mem_data_m_q & data_r),
            amoOp.AMOMAX   -> Mux(mem_data_m_q.asSInt > data_r.asSInt, mem_data_m_q, data_r),
            amoOp.AMOMIN   -> Mux(mem_data_m_q.asSInt < data_r.asSInt, mem_data_m_q, data_r),
            amoOp.AMOMAXU  -> Mux(mem_data_m_q > data_r, mem_data_m_q, data_r),
            amoOp.AMOMINU  -> Mux(mem_data_m_q < data_r, mem_data_m_q, data_r),
            amoOp.AMOOR    -> (mem_data_m_q | data_r),
            amoOp.AMOXOR   -> (mem_data_m_q ^ data_r),
            amoOp.AMOSWAP  -> mem_data_m_q
        ))

//-----------------------------------------------------------------
// Next State Logic
//-----------------------------------------------------------------
    switch(state_q) {
    //-----------------------------------------
    // STATE_RESET
    //-----------------------------------------
        is(STATE_RESET) {
            // Final line checked
            when(flush_last_q) {
                next_state_r := STATE_LOOKUP
            }
        }
    //-----------------------------------------
    // STATE_FLUSH_ADDR
    //-----------------------------------------
        is(STATE_FLUSH_ADDR) {
            next_state_r := STATE_FLUSH
        }
    //-----------------------------------------
    // STATE_FLUSH
    //-----------------------------------------
        is(STATE_FLUSH) {
            // Dirty line detected - evict unless initial cache reset cycle
            when(tag_dirty_any_m_w) {
                // Evict dirty line - else wait for dirty way to be selected
                when(evict_way_w) {
                    next_state_r := STATE_EVICT
                }
            // Final line checked, nothing dirty
            }.elsewhen(flush_last_q) {
                next_state_r := STATE_LOOKUP
            }.otherwise {
                next_state_r := STATE_FLUSH_ADDR
            }
        }
    //-----------------------------------------
    // STATE_LOOKUP
    //-----------------------------------------
        is(STATE_LOOKUP) {
            // Previous access missed in the cache
            when((mem_rd_m_q || (mem_wr_m_q =/= 0.U)) && !tag_hit_any_m_w && !mem_sc_m_q) {
                // Evict dirty line first
                when(evict_way_w) {
                    next_state_r := STATE_EVICT
                // Allocate line and fill
                }.otherwise {
                    next_state_r := STATE_REFILL
                }
            // Writeback a single line
            }.elsewhen(cpureq.writeback && io.cpu.accept) {
                next_state_r := STATE_WRITEBACK
            // Flush whole cache
            }.elsewhen(cpureq.flush && io.cpu.accept) {
                next_state_r := STATE_FLUSH_ADDR
            // Invalidate line (even if dirty)
            }.elsewhen(cpureq.invalidate && io.cpu.accept) {
                next_state_r := STATE_INVALIDATE
            // Atomic operation
            }.elsewhen(amo.isAmo && io.cpu.accept) {
                next_state_r := STATE_AMOCOMPUTE
            }
        }
    //-----------------------------------------
    // STATE_REFILL
    //-----------------------------------------
        is(STATE_REFILL) {
             // End of refill
            when(pmem_ack_w && pmem_last_w) {
                // Refill reason was write
                when(mem_wr_m_q =/= 0.U) {
                    next_state_r := STATE_WRITE
                // Refill reason was read
                }.otherwise {
                    next_state_r := STATE_READ
                }
            }
        }
    //-----------------------------------------
    // STATE_WRITE/READ/AMO
    //-----------------------------------------
        is(STATE_WRITE, STATE_AMOCOMPUTE) {
            next_state_r := STATE_LOOKUP
        }
        is(STATE_READ) {
            next_state_r := Mux(mem_amo_m_q, STATE_AMOCOMPUTE, STATE_LOOKUP)
        }
    //-----------------------------------------
    // STATE_EVICT
    //-----------------------------------------
        is(STATE_EVICT) {
            // End of evict, wait for write completion
            when(pmem_accept_w && pmem_last_w) {
                next_state_r := STATE_EVICT_WAIT
            }
        }
    //-----------------------------------------
    // STATE_EVICT_WAIT
    //-----------------------------------------
        is(STATE_EVICT_WAIT) {
            // Single line writeback
            when(pmem_ack_w && mem_writeback_m_q) {
                next_state_r := STATE_LOOKUP
            // Evict due to flush
            }.elsewhen(pmem_ack_w && flushing_q) {
                next_state_r := STATE_FLUSH_ADDR
            // Write ack, start re-fill now
            }.elsewhen(pmem_ack_w) {
                next_state_r := STATE_REFILL
            }
        }
    //-----------------------------------------
    // STATE_WRITEBACK: Writeback a cache line
    //-----------------------------------------
        is(STATE_WRITEBACK) {
            // Line is dirty - write back to memory
            when(tag_hit_and_dirty_m_w) {
                next_state_r := STATE_EVICT
            // Line not dirty, carry on
            }.otherwise {
                next_state_r := STATE_LOOKUP
            }
        }
    //-----------------------------------------
    // STATE_INVALIDATE: Invalidate a cache line
    //-----------------------------------------
        is(STATE_INVALIDATE) {
            next_state_r := STATE_LOOKUP
        }
    }

// Update state
    state_q := next_state_r

    val mem_ack_r = WireDefault(false.B)
    mem_ack_r := false.B


    when(state_q === STATE_LOOKUP) {
        // Normal hit - read or write
        when((mem_rd_m_q || (mem_wr_m_q =/= 0.U)) && tag_hit_any_m_w) {
            mem_ack_r := true.B
        }
        // Flush, invalidate, writeback, atomic
        .elsewhen(mem_flush_m_q || mem_inval_m_q || mem_writeback_m_q || mem_amo_m_q || mem_sc_m_q) {
            mem_ack_r := true.B
        }
    }

    cpuresp.ack := mem_ack_r

//-----------------------------------------------------------------
// AXI Request
//-----------------------------------------------------------------

    val pmem_rd_q  = RegInit(false.B)
    val pmem_wr0_q = RegInit(false.B)

    pmem_rd_q := Mux(pmem_rd_w, !pmem_accept_w, pmem_rd_q)

    when(state_q =/= STATE_EVICT && next_state_r === STATE_EVICT) {
        pmem_wr0_q := true.B
    }.elsewhen(pmem_accept_w) {
        pmem_wr0_q := false.B
    }

    val pmem_len_q = RegInit(0.U(p.lenW.W))
    
    when(state_q =/= STATE_EVICT && next_state_r === STATE_EVICT) {
        pmem_len_q := 7.U
    }.elsewhen(pmem_rd_w && pmem_accept_w) {
        pmem_len_q := pmem_len_w
    }.elsewhen(state_q === STATE_REFILL && pmem_ack_w) {
        pmem_len_q := pmem_len_q - 1.U
    }.elsewhen(state_q === STATE_EVICT && pmem_accept_w) {
        pmem_len_q := pmem_len_q - 1.U
    }

    pmem_last_w := (pmem_len_q === 0.U)

    val pmem_addr_q = RegInit(0.U(32.W))
    
    when((pmem_len_w.orR) && pmem_accept_w) {
        pmem_addr_q := pmem_addr_w + 4.U
    }.elsewhen(pmem_accept_w) {
        pmem_addr_q := pmem_addr_q + 4.U
    }

//-----------------------------------------------------------------
// Skid buffer for write data
//-----------------------------------------------------------------
    val pmem_wr_q = RegInit(0.U(4.W))
    val pmem_write_data_q = RegInit(0.U(32.W))

    when(pmem_wr_w.orR && !pmem_accept_w) {
        pmem_wr_q := pmem_wr_w
    }.elsewhen(pmem_accept_w) {
        pmem_wr_q := 0.U
    }

    when(!pmem_accept_w) {
        pmem_write_data_q := pmem_write_data_w
    }

// ------------------------------------------------------
// AXI Error Handling
// ------------------------------------------------------
    val error_q = RegInit(false.B)
    
    when(pmem_ack_w && pmem_error_w) {
        error_q := true.B
    }.elsewhen(cpuresp.ack) {
        error_q := false.B
    }

    cpuresp.error := error_q    

//-----------------------------------------------------------------
// Outport
//-----------------------------------------------------------------
    val refill_request_w = (state_q =/= STATE_REFILL && next_state_r === STATE_REFILL)
    val evict_request_w  = (state_q === STATE_EVICT) && (evict_way_w || mem_writeback_m_q)


    // AXI Read/Write channel
    pmem_rd_w         := (refill_request_w || pmem_rd_q)
    pmem_wr_w         := Mux(evict_request_w || pmem_wr_q.orR, "hF".U(4.W), 0.U(4.W))
    pmem_addr_w       := Mux(pmem_len_w.orR,
                        Mux(pmem_rd_w,
                            Cat(mem_addr_m_q(31, p.tagReqLineL), 0.U(p.offBits.W)),
                            Cat(evict_addr_w, 0.U(p.offBits.W))),
                        pmem_addr_q
                        )
    pmem_len_w        := Mux(refill_request_w || pmem_rd_q || (state_q === STATE_EVICT && pmem_wr0_q), 7.U, 0.U)
    pmem_write_data_w := Mux(pmem_wr_q.orR, pmem_write_data_q, evict_data_w)

    // Outport signals
    pmemreq.wr         := pmem_wr_w
    pmemreq.rd         := pmem_rd_w
    pmemreq.len        := pmem_len_w
    pmemreq.addr       := pmem_addr_w
    pmemreq.writeData  := pmem_write_data_w

    pmem_accept_w        := pmemresp.accept
    pmem_ack_w           := pmemresp.ack
    pmem_error_w         := pmemresp.error
    pmem_read_data_w     := pmemresp.readData

    // val dbg_state = Wire(UInt(80.W))
    // dbg_state := "RESET".U

    // switch(state_q) {
    //     is(STATE_RESET)       { dbg_state := "RESET" }
    //     is(STATE_FLUSH_ADDR)  { dbg_state := "FLUSH_ADDR" }
    //     is(STATE_FLUSH)       { dbg_state := "FLUSH" }
    //     is(STATE_LOOKUP)      { dbg_state := "LOOKUP" }
    //     is(STATE_READ)        { dbg_state := "READ" }
    //     is(STATE_WRITE)       { dbg_state := "WRITE" }
    //     is(STATE_REFILL)      { dbg_state := "REFILL" }
    //     is(STATE_EVICT)       { dbg_state := "EVICT" }
    //     is(STATE_EVICT_WAIT)  { dbg_state := "EVICT_WAIT" }
    //     is(STATE_INVALIDATE)  { dbg_state := "INVAL" }
    //     is(STATE_WRITEBACK)   { dbg_state := "WRITEBACK" }
    // }
}