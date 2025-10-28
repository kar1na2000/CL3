package cl3

import chisel3._
import chisel3.util._

class ICache(p: ICacheParams) extends Module {
    val io = IO(new ICacheIO(p))

    val (cpu, aw, w, b, ar, r) = (io.cpu, io.axi.aw, io.axi.w, io.axi.b, io.axi.ar, io.axi.r)

    cpu.resp_accept := false.B
    cpu.resp_error  := false.B
    cpu.resp_inst   := 0.U

    aw.valid := false.B
    aw.bits  := 0.U.asTypeOf(io.axi.aw.bits)
    w.valid  := false.B
    w.bits   := 0.U.asTypeOf(io.axi.w.bits)
    ar.valid := false.B
    ar.bits  := 0.U.asTypeOf(io.axi.ar.bits)
    b.ready  := true.B
    r.ready  := true.B
    dontTouch(ar)
    dontTouch(r)
    val STATE_W = 2
    val states = Enum(5)
    val STATE_FLUSH    = states(0)
    val STATE_LOOKUP   = states(1)
    val STATE_REFILL   = states(2)
    val STATE_RELOOKUP = states(3)
    // val STATE_DONE     = states(4)
    val state_q = RegInit(STATE_FLUSH)
    val next_state_r = WireDefault(state_q)
    dontTouch(next_state_r)
    
    val invalidate_q = RegInit(false.B)
    val replace_way_q = RegInit(0.U(1.W))

    val req_line_addr_w = cpu.req_pc(p.tagReqLineH, p.tagReqLineL)
    val CACHE_DATA_ADDR_W = p.lineAddrBits + p.lineSizeBits - 3
    val req_data_addr_w = cpu.req_pc(CACHE_DATA_ADDR_W+2, 3)
    val flush_addr_q    = RegInit(0.U(p.tagReqLineW.W));

//-----------------------------------------------------------------
// Lookup validation
//-----------------------------------------------------------------
    val lookup_valid_q = RegInit(false.B)
    when(cpu.req_rd && cpu.resp_accept) {
        lookup_valid_q := true.B 
    } .elsewhen(cpu.resp_valid) { 
        lookup_valid_q := false.B 
    }

//-----------------------------------------------------------------
// Lookup address
//-----------------------------------------------------------------
    val lookup_addr_q = RegInit(0.U(p.pcBits.W))
    lookup_addr_q := Mux(cpu.req_rd && cpu.resp_accept, cpu.req_pc, lookup_addr_q)
    
    val req_pc_tag_cmp_w = lookup_addr_q(p.tagCmpAddrH, p.tagCmpAddrL)

//-----------------------------------------------------------------
// TAG RAMS
//-----------------------------------------------------------------
    val tag_addr_r = Wire(UInt(p.tagReqLineW.W))
    tag_addr_r := flush_addr_q
    dontTouch(tag_addr_r)

// Tag RAM address

    // Cache flush
    when(state_q === STATE_FLUSH) {
        tag_addr_r := flush_addr_q
    // Line refill
    } .elsewhen(state_q === STATE_REFILL || state_q === STATE_RELOOKUP) {
        tag_addr_r := lookup_addr_q(p.tagReqLineH, p.tagReqLineL)
    // Lookup
    } .otherwise {
        tag_addr_r := req_line_addr_w
    }

// Tag RAM write data
    val tag_data_in_r = Wire(UInt(p.tagRamDataBits.W))
    tag_data_in_r := 0.U
    
    // Cache flush
    when(state_q === STATE_FLUSH) {
        tag_data_in_r := 0.U
    // Line refill
    } .elsewhen(state_q === STATE_REFILL) {
        tag_data_in_r := Cat(1.U(1.W), lookup_addr_q(p.tagCmpAddrH, p.tagCmpAddrL)) // valid = 1, tag = addr
    }

// Tag RAM write enable
    // val tag0_data_out_w = Wire(UInt(p.tagRamDataBits.W))
    // val tag1_data_out_w = Wire(UInt(p.tagRamDataBits.W))
    val tag_data_out_w  = Wire(Vec(p.numWays, UInt(p.tagRamDataBits.W)))
    dontTouch(tag_data_out_w)
    val tag0_valid_w    = tag_data_out_w(0)(p.cacheTagValidBit)
    val tag0_addr_bits_w = tag_data_out_w(0)(p.cacheTagAddrBits, 0)
    val tag1_valid_w    = tag_data_out_w(1)(p.cacheTagValidBit)
    val tag1_addr_bits_w = tag_data_out_w(1)(p.cacheTagAddrBits, 0)
    val u_tag = Seq.fill(p.numWays)(Module(new ICacheTagRam(p)))
    for (i <- 0 until p.numWays) {
        val tag_write_r = WireDefault(false.B)
        // Cache flush
        when(state_q === STATE_FLUSH) {
            tag_write_r := true.B
        // Line refill
        } .elsewhen(state_q === STATE_REFILL) {
            tag_write_r := r.valid && r.bits.last && (replace_way_q === i.U)
        }
        val tagRam = u_tag(i).io
        tagRam.addr := tag_addr_r
        tagRam.din  := tag_data_in_r
        tagRam.wr   := tag_write_r
        tag_data_out_w(i) := tagRam.dout
    }

    // Tag hit
    val tag0_hit_w = tag0_valid_w && (tag0_addr_bits_w === req_pc_tag_cmp_w)
    val tag1_hit_w = tag1_valid_w && (tag1_addr_bits_w === req_pc_tag_cmp_w)

    val tag_hit_any_w = tag0_hit_w || tag1_hit_w

//-----------------------------------------------------------------
// DATA RAMS
//-----------------------------------------------------------------
    val data_addr_r        = Wire(UInt(CACHE_DATA_ADDR_W.W))
    val data_write_addr_q = RegInit(0.U(CACHE_DATA_ADDR_W.W))
    val refill_word_idx_q = RegInit(0.U(3.W))
    val refill_lower_q    = RegInit(0.U(p.axi.axiDataBits.W))

    when(r.valid && r.bits.last){
        refill_word_idx_q := 0.U
    } .elsewhen(r.valid) {
        refill_word_idx_q := refill_word_idx_q + 1.U
    }

    refill_lower_q := Mux(r.valid, r.bits.data, refill_lower_q)

// Data RAM refill write address
    when( state_q === STATE_LOOKUP && next_state_r === STATE_REFILL ){
        data_write_addr_q := ar.bits.addr(CACHE_DATA_ADDR_W+2, 3)
    } .elsewhen(state_q === STATE_REFILL && r.valid && refill_word_idx_q(0)) {
        data_write_addr_q := data_write_addr_q + 1.U
    }

// Data RAM address
    data_addr_r := req_data_addr_w
    when(state_q === STATE_REFILL){
        data_addr_r := data_write_addr_q
    } .elsewhen(state_q === STATE_RELOOKUP){
        data_addr_r := lookup_addr_q(CACHE_DATA_ADDR_W+2, 3)
    } .otherwise {
        data_addr_r := req_data_addr_w
    }

// Data RAM write enable
    // val data0_out = Wire(UInt(p.dataRamDataBits.W))
    // val data1_out = Wire(UInt(p.dataRamDataBits.W))
    val data_out  = Wire(Vec(p.numWays, UInt(p.dataRamDataBits.W)))
    val u_data = Seq.fill(p.numWays)(Module(new ICacheDataRam(p)))
    for (i <- 0 until p.numWays) {
        val data_write_r = WireDefault(false.B)
        data_write_r := r.valid && (replace_way_q === i.U)

        val dataRam = u_data(i).io
        dataRam.addr := data_addr_r
        dataRam.din  := Cat(r.bits.data, refill_lower_q)
        dataRam.wr   := data_write_r
        data_out(i) := dataRam.dout
    }

//-----------------------------------------------------------------
// Flush counter
//-----------------------------------------------------------------
    when(state_q === STATE_FLUSH){
        flush_addr_q := flush_addr_q + 1.U
    } .elsewhen(cpu.req_invalidate && cpu.resp_accept){
        flush_addr_q := req_line_addr_w
    } .otherwise{
        flush_addr_q := 0.U
    }

//-----------------------------------------------------------------
// Replacement Policy
//----------------------------------------------------------------- 
// Using random replacement policy - this way we cycle through the ways
// when needing to replace a line.
    replace_way_q := Mux(r.valid && r.bits.last, replace_way_q + 1.U, replace_way_q)

//-----------------------------------------------------------------
// Instruction Output
//-----------------------------------------------------------------
    // cpu.resp_valid := lookup_valid_q && (state_q === STATE_DONE) && tag_hit_any_w
    cpu.resp_valid := lookup_valid_q && (state_q === STATE_LOOKUP) && tag_hit_any_w

    

    val inst_r = Wire(UInt(p.instBits.W))
    inst_r := data_out(0)
    
    when(tag0_hit_w){
        inst_r := data_out(0)
    } .elsewhen(tag1_hit_w){
        inst_r := data_out(1)
    }

    cpu.resp_inst := inst_r

    switch(state_q) {
        is(STATE_FLUSH){
            when(invalidate_q) {
                next_state_r := STATE_LOOKUP
            } .elsewhen(flush_addr_q === Fill(p.lineAddrBits, 1.U(1.W))) {
                next_state_r := STATE_LOOKUP
            }
        }
        is(STATE_LOOKUP){
            when(lookup_valid_q && !tag_hit_any_w) {
                next_state_r := STATE_REFILL
            } .elsewhen(cpu.req_invalidate || cpu.req_flush ) {
                next_state_r := STATE_FLUSH
            }
        }
        is(STATE_REFILL){
            when(r.valid && r.bits.last) {
                next_state_r := STATE_RELOOKUP
            }
        }
        is(STATE_RELOOKUP){
            // next_state_r := STATE_DONE
            next_state_r := STATE_LOOKUP

        }
        // is(STATE_DONE){
        //     next_state_r := STATE_LOOKUP
        // }
    }

// Update state
    state_q := next_state_r
    cpu.resp_accept := (state_q === STATE_LOOKUP && next_state_r =/= STATE_REFILL)

//-----------------------------------------------------------------
// Invalidate
//-----------------------------------------------------------------
    invalidate_q := Mux(cpu.req_invalidate && cpu.resp_accept, true.B, false.B)

//-----------------------------------------------------------------
// AXI Request Hold
//-----------------------------------------------------------------
    val arvalid_q = RegInit(false.B)
    arvalid_q := Mux(ar.valid && !ar.ready, true.B, false.B)

//-----------------------------------------------------------------
// AXI Error Handling
//-----------------------------------------------------------------
    val axi_state = RegInit(true.B)
    when(r.valid && r.ready && r.bits.last){
        axi_state := true.B
    } .elsewhen( ar.valid && ar.ready ) {
        axi_state := false.B
    }
    val axi_error_q = RegInit(false.B)
    when(r.valid && r.ready && r.bits.resp =/= 0.U) {
        axi_error_q := true.B
    } .elsewhen(cpu.resp_valid) {
        axi_error_q := false.B
    }
    cpu.resp_error := axi_error_q

    ar.valid := (state_q === STATE_REFILL && next_state_r === STATE_REFILL) && axi_state
    ar.bits.addr  := Cat(lookup_addr_q(p.axi.axiAddrBits-1, p.lineSizeBits), 0.U(p.lineSizeBits.W))
    ar.bits.burst := 1.U // INCR
    ar.bits.id    := p.axiIdDefault.U
    ar.bits.len   := p.axi.axiLenBits.U - 1.U
    ar.bits.cache := "b0110".U(4.W) // 0010 Cacheable，Nonbufferable
    ar.bits.lock  := 0.U
    ar.bits.size  := (log2Ceil(p.axi.axiDataBits / 8)).U
    // ar.bits.port  := "b101".U(3.W) // default: instruction access, secure, privileged

    // ar.bits.qos   := 0.U
    // ar.bits.user  := 0.U
    // ar.bits.region:= 0.U
    // when(state_q === STATE_LOOKUP && next_state_r =/= STATE_REFILL && cpu.req_rd) {
    //         state_q := STATE_DONE
    //     }
}