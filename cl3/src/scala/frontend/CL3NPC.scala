package cl3

import chisel3._
import chisel3.util._

trait NPCConfig {
  val numBtbEntries:      Int = 32
  val numBhtEntries:      Int = 512
  val numRasEntries:      Int = 8
  val numBtbEntriesWidth: Int = log2Ceil(numBtbEntries)
  val numBhtEntriesWidth: Int = log2Ceil(numBhtEntries)
  val numRasEntriesWidth: Int = log2Ceil(numRasEntries)
} //TODO: add require clause

class CL3NPC() extends Module with NPCConfig with CL3Config {
  val io = IO(new NPCFullIO())

  val pc_plus_8 = (io.info.pc(31, 3) ## 0.U(3.W)) + 8.U

  if (EnableBP) {

    // --- BTB (Branch Target Buffer) ---

    // TODO: move BTB to a separate class and use SRAM instead of Reg
    class BtbEntry extends Bundle {
      val pc     = UInt(32.W)
      val target = UInt(32.W)
      val isCall = Bool()
      val isRet  = Bool()
      val isJmp  = Bool()
    }

    val btb = RegInit(VecInit(Seq.fill(numBtbEntries)(0.U.asTypeOf(new BtbEntry))))

    val btb_hit_lo_vec = VecInit(btb.map(_.pc === io.info.pc))
    val btb_hit_lo     = btb_hit_lo_vec.asUInt.orR

    val check_hi       = !btb_hit_lo && !io.info.pc(2)
    val btb_hit_hi_vec = VecInit(btb.map(_.pc === (io.info.pc | 4.U)))
    val btb_hit_hi     = btb_hit_hi_vec.asUInt.orR && check_hi

    val btb_hit = btb_hit_hi || btb_hit_lo

    // TODO: make sure 'OHToUInt' is suitable here
    val btb_idx   = Mux(btb_hit_lo, OHToUInt(btb_hit_lo_vec), OHToUInt(btb_hit_hi_vec))
    val btb_entry = btb(btb_idx)

    // --- BTB Update Logic (on branch resolution) ---

    // TODO: the update logic should change when BTB is SRAM
    val btb_hit_br_vec = VecInit(btb.map(_.pc === io.bp.source))
    val btb_hit_br     = btb_hit_br_vec.asUInt.orR
    val btb_br_miss    = io.bp.valid && !btb_hit_br
    val btb_br_idx     = OHToUInt(btb_hit_br_vec)

    val lfsr = Module(new LFSR(numBtbEntriesWidth))
    lfsr.io.alloc := btb_br_miss
    val btb_alloc_idx = lfsr.io.allocEntry

    val btb_wr_idx = Mux(btb_hit_br, btb_br_idx, btb_alloc_idx)

    // TODO: we don't need to change BTB in some cases
    when(io.bp.valid) {

      btb(btb_wr_idx).pc     := io.bp.source
      btb(btb_wr_idx).isCall := io.bp.isCall
      btb(btb_wr_idx).isRet  := io.bp.isRet
      btb(btb_wr_idx).isJmp  := io.bp.isJmp

      // Update target only if it was a taken branch or a new entry
      when(io.bp.isTaken || !btb_hit_br) {
        btb(btb_wr_idx).target := io.bp.target
      }
    }

    // --- RAS (Return Address Stack) ---

    val ras_q = RegInit(VecInit(Seq.fill(numRasEntries)(1.U)))

    // RAS Index (speculative)
    val ras_idx_spe_q = RegInit(0.U(numRasEntriesWidth.W))

    // RAS Index (actually), update when branch is resolved
    val ras_idx_real_q = RegInit(0.U(numRasEntriesWidth.W))
    when(io.bp.valid && io.bp.isCall) {
      ras_idx_real_q := ras_idx_real_q + 1.U
    }.elsewhen(io.bp.valid && io.bp.isRet) {
      ras_idx_real_q := ras_idx_real_q - 1.U
    }

    val ras_npc = ras_q(ras_idx_spe_q)

    // The LSB of ras_npc is 1 means the address is invalid
    val pc_is_call = btb_hit && btb_entry.isCall && !ras_npc(0)
    val pc_is_ret  = btb_hit && btb_entry.isRet && !ras_npc(0)

    val ras_idx = MuxCase(
      ras_idx_spe_q,
      Seq(
        (io.bp.valid && io.bp.isCall)  -> (ras_idx_real_q + 1.U),
        (io.bp.valid && io.bp.isRet)   -> (ras_idx_real_q - 1.U),
        (pc_is_call && io.info.accept) -> (ras_idx_spe_q + 1.U),
        (pc_is_ret && io.info.accept)  -> (ras_idx_spe_q - 1.U)
      )
    )

    when(io.bp.valid && io.bp.isCall) {
      ras_q(ras_idx) := io.bp.source + 4.U
    }.elsewhen(pc_is_call && io.info.accept) {
      ras_q(ras_idx) := Mux(btb_hit_hi, io.info.pc | 4.U, io.info.pc) + 4.U
    }

    // --- BHT (Branch History Table) & G-Share ---

    // Global history Register (actually), update when branch is resolved
    val ghr_real_q = RegInit(0.U(numBhtEntriesWidth.W))
    when(io.bp.valid && (io.bp.isTaken || io.bp.isNotTaken)) {
      ghr_real_q := ghr_real_q(numBhtEntriesWidth - 2, 0) ## io.bp.isTaken
    }

    // Global history Register (speculative)
    val ghr_q = RegInit(0.U(numBhtEntriesWidth.W))

    val pred_taken     = Wire(Bool())
    val pred_not_taken = Wire(Bool())

    when(io.bp.valid) {
      ghr_q := ghr_real_q(numBhtEntriesWidth - 2, 0) ## io.bp.isTaken
    }.elsewhen(pred_taken || pred_not_taken) {
      ghr_q := ghr_q(numBhtEntriesWidth - 2, 0) ## pred_taken
    }

    // Gshare
    val bht_rd_idx = ghr_q ^ (io.info.pc(3 + numBhtEntriesWidth - 2, 3) ## btb_hit_hi)

    val bht_q        = RegInit(VecInit(Seq.fill(numBhtEntries)(3.U(2.W))))
    val bht_is_taken = bht_q(bht_rd_idx) >= 2.U

    // BHT Update
    val wr_ghr     = Mux(io.bp.valid, ghr_real_q, ghr_q)
    val bht_wr_idx = wr_ghr ^ io.bp.source(2 + numBhtEntriesWidth - 1, 2)

    val bht_data   = bht_q(bht_wr_idx)
    when(io.bp.isTaken && bht_data =/= 3.U) {
      bht_q(bht_wr_idx) := bht_data + 1.U
    }.elsewhen(io.bp.isNotTaken && bht_data =/= 0.U) {
      bht_q(bht_wr_idx) := bht_data - 1.U
    }
    val bp_trigger = btb_hit && (pc_is_ret || bht_is_taken || btb_entry.isJmp)
    pred_taken     := bp_trigger && io.info.accept
    pred_not_taken := btb_hit && !pred_taken && io.info.accept

    io.info.npc := Mux(pc_is_ret, ras_npc, Mux(btb_hit && (bht_is_taken || btb_entry.isJmp), btb_entry.target, pc_plus_8))

    io.info.taken := Mux(bp_trigger, Mux(io.info.pc(2), btb_hit_hi ## 0.U(1.W), btb_hit_hi ## !btb_hit_hi), 0.U(2.W))

  } else {
    io.info.npc    := pc_plus_8
    io.info.taken := false.B
  }
}
