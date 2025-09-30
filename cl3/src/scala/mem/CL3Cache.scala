package cl3

import chisel3._
import chisel3.util._
import LSUConstant._

class CL3ICache extends Module {

  val io = IO(new Bundle {

    val in = new Bundle {
      val req  = Flipped(Decoupled(Input(new SimpleMemReq(32))))
      val resp = Decoupled(Output(new SimpleMemResp(32)))
    }

    val out = new Bundle {
      val req  = Decoupled(Output(new SimpleMemReq(32)))
      val resp = Flipped(Decoupled(Input(new SimpleMemResp(32))))
    }

    // val flush = Input(Bool())
  })

  dontTouch(io)

  val sInvalid :: sIdle :: sLookup :: sMiss :: sRefill :: Nil = Enum(5)
  val state                                                   = RegInit(sInvalid)

  val stateInvalid: Bool = state === sInvalid
  val stateIdle:    Bool = state === sIdle
  val stateLookup:  Bool = state === sLookup
  val stateMiss:    Bool = state === sMiss
  val stateRefill:  Bool = state === sRefill

  val clearIdx = RegInit(0.U(9.W))
  when(stateInvalid) {
    clearIdx := clearIdx + 1.U
  }.otherwise {
    clearIdx := 0.U // TODO: fence.i
  }

  val reqBuffer = RegInit(0.U.asTypeOf(new SimpleMemReq(32)))
  when(io.in.req.fire) {
    reqBuffer := io.in.req.bits
  }

  when(stateInvalid) {
    when(clearIdx === 255.U) {
      state := sIdle
    }
  }

  when(stateIdle) {
    when(io.in.req.fire) {
      state := sLookup
    }
  }

  val hit = Wire(Bool())
  when(stateLookup) {
    when(!hit) {
      state := sMiss
    }.elsewhen(!io.in.req.fire) {
      state := sIdle
    }
  }

  when(stateMiss) {
    when(io.out.req.fire) {
      state := sRefill
    }
  }

  val refillCnt = RegInit(0.U(2.W))
  when(io.out.resp.fire) {
    refillCnt := refillCnt + 1.U
  }

  val refillFinish = refillCnt === 3.U && io.out.resp.fire
  when(stateRefill) {
    when(refillFinish) {
      state     := sIdle
      refillCnt := 0.U
    }
  }

  val reqIdx    = io.in.req.bits.addr(11, 4)
  val bufferIdx = reqBuffer.addr(11, 4)

  val ageTable = RegInit(VecInit(Seq.fill(256)(0.U(1.W))))
  val hitWay   = Wire(UInt(1.W))

  val replaceWay = ageTable(bufferIdx)

  when(stateLookup && hit) {
    ageTable(bufferIdx) := ~hitWay
  }.elsewhen(stateMiss && io.out.req.fire) {
    ageTable(bufferIdx) := ~replaceWay
  }

  val clearData  = 0.U.asTypeOf(new TAGVBundle)
  val refillData = Wire(new TAGVBundle)
  refillData.tag := reqBuffer.addr(31, 12)
  refillData.v   := true.B

  val tagvClear  = stateInvalid
  val tagvRefill = stateRefill && refillFinish
  val tagvRead   = (stateIdle || stateLookup) && io.in.req.valid

  val tagvRdAddr = reqIdx
  val tagvWrAddr = Mux(tagvClear, clearIdx, bufferIdx)
  val tagvAddr   = Mux(tagvRead, tagvRdAddr, tagvWrAddr)
  val tagvWrData = Mux(tagvClear, clearData, refillData)

  class TAGVBundle extends Bundle {
    val tag = UInt(20.W)
    val v   = Bool()
  }

  val tagvTable = VecInit(
    Seq.fill(2)(
      SRAM(
        size = 256,
        tpe = new TAGVBundle,
        numReadPorts = 0,
        numWritePorts = 0,
        numReadwritePorts = 1
      )
    )
  )

  for (i <- 0 until tagvTable.length) {

    val en = tagvClear || tagvRead || (tagvRefill && (replaceWay === i.U))

    tagvTable(i).readwritePorts(0).address   := tagvAddr
    tagvTable(i).readwritePorts(0).isWrite   := tagvClear || tagvRefill
    tagvTable(i).readwritePorts(0).enable    := en
    tagvTable(i).readwritePorts(0).writeData := tagvWrData

  }
  // 4 Banks
  val dataTable = VecInit(
    Seq.fill(4)(
      SRAM.masked(
        size = 256,
        tpe = Vec(2, UInt(32.W)),
        numReadPorts = 0,
        numWritePorts = 0,
        numReadwritePorts = 1
      )
    )
  )

  dontTouch(dataTable)

  val dataRead   = (stateIdle || stateLookup) && io.in.req.valid
  val dataRefill = stateRefill && io.out.resp.fire

  val dataRdAddr = reqIdx
  val dataWrAddr = bufferIdx
  val dataAddr   = Mux(dataRead, dataRdAddr, dataWrAddr)
  val dataWrData = VecInit(Seq.fill(2)(io.out.resp.bits.rdata))

  val bankWrSel = refillCnt
  val bankRdSel = io.in.req.bits.addr(3, 2)

  for (i <- 0 until dataTable.length) {

    val en = (dataRead && bankRdSel === i.U) ||
      (dataRefill && bankWrSel === i.U)
    dataTable(i).readwritePorts(0).address   := dataAddr
    dataTable(i).readwritePorts(0).isWrite   := dataRefill
    dataTable(i).readwritePorts(0).enable    := en
    dataTable(i).readwritePorts(0).writeData := dataWrData

    val mask = dataTable(i).readwritePorts(0).mask.get
    mask := VecInit(replaceWay, !replaceWay)
  }

// Lookup
  val cacheable = reqBuffer.cacheable

  val searchTag = reqBuffer.addr(31, 12)
  val tagv      = tagvTable.map { t =>
    t.readwritePorts(0).readData
  }
  val hitVec    = VecInit(tagv.map { data =>
    data.v && (data.tag === searchTag)
  })
  hit := hitVec.asUInt.orR && cacheable

  val raw64data = dataTable(reqBuffer.addr(3, 2))
    .readwritePorts(0)
    .readData
  hitWay := Mux(hitVec(0), 1.U, 0.U)

  io.in.req.ready := Mux1H(
    Seq(
      stateInvalid -> false.B,
      stateIdle    -> true.B,
      stateLookup  -> hit,
      stateMiss    -> false.B,
      stateRefill  -> false.B
    )
  )

  val bankMatch = refillCnt === reqBuffer.addr(3, 2)
  io.in.resp.valid := Mux1H(
    Seq(
      stateInvalid -> false.B,
      stateIdle    -> false.B,
      stateLookup  -> hit,
      stateMiss    -> false.B,
      stateRefill  -> (io.out.resp.valid && bankMatch)
    )
  )

  io.in.resp.bits.err   := false.B // TODO
  io.in.resp.bits.rdata := Mux(stateRefill, io.out.resp.bits.rdata, raw64data(hitWay))

  io.out.req.valid          := stateMiss
  io.out.req.bits.addr      := reqBuffer.addr(31, 4) ## 0.U(4.W)
  io.out.req.bits.mask      := Mux(cacheable, MASK_ALL, reqBuffer.mask)
  io.out.req.bits.size      := Mux(cacheable, 2.U, reqBuffer.size)
  io.out.req.bits.wdata     := 0.U
  io.out.req.bits.wen       := false.B
  io.out.req.bits.cacheable := cacheable
  io.out.resp.ready         := stateRefill

}

class DCacheWrBundle extends Bundle {
  val wdata = UInt(128.W)
  val waddr = UInt(32.W)
  val wstrb = UInt(4.W)
  val wsize = UInt(3.W)
}
// 2-ways Dcache, change in the future
class CL3DCache      extends Module {

  val io = IO(new Bundle {

    val cpu = new Bundle {
      val req  = Flipped(Decoupled(Input(new SimpleMemReq(32))))
      val resp = Decoupled(Output(new SimpleMemResp(32)))
    }

    val rd = new Bundle {
      val req  = Decoupled(Output(new SimpleMemReq(32)))
      val resp = Flipped(Decoupled(Input(new SimpleMemResp(32))))
    }

    val wr = Decoupled(Output(new DCacheWrBundle))

    // val flush = Input(Bool()), reserve for fence.i interface
  })

  dontTouch(io)
  /* --------------------------     main FSM      ----------------------------- */

  // See https://gitee.com/loongson-edu/open-la500/blob/master/dcache.v
  val sInvalid :: sIdle :: sLookup :: sMiss :: sReplace :: sRefill :: Nil = Enum(6)
  val state                                                               = RegInit(sInvalid)

  val stateInvalid: Bool = state === sInvalid
  val stateIdle:    Bool = state === sIdle
  val stateLookup:  Bool = state === sLookup
  val stateMiss:    Bool = state === sMiss
  val stateReplace: Bool = state === sReplace
  val stateRefill:  Bool = state === sRefill

  val replaceWay = Wire(UInt(1.W))

  // clear each line when cache is invalid
  val clearIdx = RegInit(0.U(9.W))
  when(stateInvalid) {
    clearIdx := clearIdx + 1.U
  }.otherwise {
    clearIdx := 0.U // TODO: fence.i
  }

  val reqBuffer = RegInit(0.U.asTypeOf(new SimpleMemReq(32)))

  val cacheable = reqBuffer.cacheable
  val isStore   = reqBuffer.wen
  val reqIdx    = reqBuffer.addr(11, 4)

  val isUncachedWrite = isStore && !cacheable

  when(io.cpu.req.fire) {
    reqBuffer := io.cpu.req.bits
  }

  when(stateInvalid) {
    when(clearIdx === 255.U) {
      state := sIdle
    }
  }

  when(stateIdle) {
    when(io.cpu.req.fire) {
      state := sLookup
    }
  }

  val hit = Wire(Bool())
  when(stateLookup) {
    when(!hit) {
      state := sMiss
    }.elsewhen(!io.cpu.req.fire) {
      state := sIdle
    }
  }

  val dirty       = Wire(Bool())
  val replaceFlag = RegInit(false.B)
  when(stateMiss) {
    when(io.wr.ready) {
      state := sReplace
    }

    when(dirty && cacheable) {
      replaceFlag := false.B
    }.otherwise {
      replaceFlag := true.B
    }
  }

//send read and write state
  when(stateReplace) {
    when(io.rd.req.ready || isUncachedWrite) {
      state := sRefill
    }

    when(io.wr.fire) {
      replaceFlag := true.B
    }
  }

  val refillCnt = RegInit(0.U(2.W))
  when(stateReplace && (io.rd.req.ready || isUncachedWrite)) {
    refillCnt := Mux(cacheable, 0.U, 3.U)
  }.elsewhen(io.rd.resp.fire) {
    refillCnt := refillCnt + 1.U
  }

  when(stateRefill) {
    when(refillCnt === 3.U && io.rd.resp.fire || isUncachedWrite) {
      state := sIdle
    }
  }

  /* --------------------------     write FSM      ----------------------------- */

  val writeBuffer = RegInit(0.U.asTypeOf(new SimpleMemReq(32)))
  val hitWayReg   = RegInit(0.U(1.W))
  val hitWrite    = stateLookup && isStore && hit

  val hitWay = Wire(UInt(1.W))

  val sHang :: sWrite :: Nil = Enum(2) // TODO: change the name "Hang"
  val wrState                = RegInit(sHang)

  val stateHang:  Bool = wrState === sHang
  val stateWrite: Bool = wrState === sWrite

  when(stateHang && hitWrite) {
    wrState := sWrite
  }

  when(stateWrite && !hitWrite) {
    wrState := sHang
  }

  when(hitWrite) {
    writeBuffer := reqBuffer
    hitWayReg   := hitWay
  }

  /* --------------------------     dirty Table      ----------------------------- */
  val dirtyTable0 = RegInit(VecInit(Seq.fill(256)(false.B)))
  val dirtyTable1 = RegInit(VecInit(Seq.fill(256)(false.B)))

  dirty := Mux(replaceWay.asBool, dirtyTable1(reqIdx), dirtyTable0(reqIdx))

  val updateDirty =
    stateRefill && refillCnt === 3.U && io.rd.resp.fire && cacheable ||
      stateWrite

  val updateDirtyIdx   = Mux(stateWrite, writeBuffer.addr(11, 4), reqIdx)
  val updateDirtyWay   = Mux(stateWrite, hitWayReg, replaceWay)
  val updateDirtyValue = Mux(stateWrite, true.B, Mux(isStore, true.B, false.B))

  when(updateDirty && updateDirtyWay === 0.U) {
    dirtyTable0(updateDirtyIdx) := updateDirtyValue
  }
  when(updateDirty && updateDirtyWay === 1.U) {
    dirtyTable1(updateDirtyIdx) := updateDirtyValue
  }

  /* --------------------------      TAGV Table      ----------------------------- */

  val clearTAGVData  = 0.U.asTypeOf(new TAGVBundle)
  val refillTAGVData = Wire(new TAGVBundle)
  refillTAGVData.tag := reqBuffer.addr(31, 12)
  refillTAGVData.v   := true.B

  val tagvClear   = stateInvalid
  val tagvRefill  = stateRefill && refillCnt === 3.U && io.rd.resp.fire
  // val tagvRead   = (stateIdle || stateLookup) && io.cpu.req.fire
  val tagvRead    = (stateIdle || stateLookup)
  val tagvReplace = stateMiss && io.wr.ready && cacheable

  val tagvRdAddr = Mux(tagvRead, io.cpu.req.bits.addr(11, 4), reqBuffer.addr(11, 4))
  val tagvWrAddr = Mux(tagvClear, clearIdx, reqBuffer.addr(11, 4))
  val tagvAddr   = Mux(tagvRead || tagvReplace, tagvRdAddr, tagvWrAddr)
  val tagvWrData = Mux(tagvClear, clearTAGVData, refillTAGVData)

  class TAGVBundle extends Bundle {
    val tag = UInt(20.W)
    val v   = Bool()
  }
  val tagvTable = VecInit(
    Seq.fill(2)(
      SRAM(
        size = 256,
        tpe = new TAGVBundle,
        numReadPorts = 0,
        numWritePorts = 0,
        numReadwritePorts = 1
      )
    )
  )

  for (i <- 0 until tagvTable.length) {

    val en = tagvClear || tagvRead || tagvReplace ||
      (cacheable && tagvRefill && (replaceWay === i.U))

    tagvTable(i).readwritePorts(0).address   := tagvAddr
    tagvTable(i).readwritePorts(0).isWrite   := tagvClear || tagvRefill
    tagvTable(i).readwritePorts(0).enable    := en
    tagvTable(i).readwritePorts(0).writeData := tagvWrData

  }

  /* --------------------------     data Table      ----------------------------- */

  // 4 Banks
  val dataTable = VecInit(
    Seq.fill(4)(
      SRAM.masked(
        size = 256,
        tpe = Vec(8, UInt(8.W)), // 2-way, byte masked
        numReadPorts = 0,
        numWritePorts = 0,
        numReadwritePorts = 1
      )
    )
  )

  dontTouch(dataTable)

  val dataRead    = (stateIdle || stateLookup) && io.cpu.req.fire && !io.cpu.req.bits.wen
  val dataReplace = stateMiss && dirty && io.wr.ready && cacheable
  val dataRefill  = stateRefill && io.rd.resp.fire && cacheable
  val dataWrite   = stateWrite

  val rdata = io.rd.resp.bits.rdata
  val wdata = reqBuffer.wdata

  val mask32     = Cat(
    Fill(8, reqBuffer.mask(3)),
    Fill(8, reqBuffer.mask(2)),
    Fill(8, reqBuffer.mask(1)),
    Fill(8, reqBuffer.mask(0))
  )
  val realMask   = Mux(isStore && refillCnt === reqBuffer.addr(3, 2), mask32, 0.U(32.W))
  val mergedData = Fill(2, (rdata & ~realMask) | (wdata & realMask))
    .asTypeOf(Vec(8, UInt(8.W)))

  val dataRdAddr = Mux(dataRead, io.cpu.req.bits.addr(11, 4), reqBuffer.addr(11, 4))
  val dataWrAddr = Mux(dataWrite, writeBuffer.addr(11, 4), reqBuffer.addr(11, 4))
  val dataWrMask = Mux(
    dataWrite,
    Mux(hitWayReg.asBool, writeBuffer.mask ## 0.U(4.W), 0.U(4.W) ## writeBuffer.mask),
    Mux(replaceWay.asBool, MASK_ALL ## 0.U(4.W), 0.U(4.W) ## MASK_ALL)
  )

  val bankWrSel = Mux(dataWrite, writeBuffer.addr(3, 2), refillCnt)
  val bankRdSel = Mux(dataRead, io.cpu.req.bits.addr(3, 2), reqBuffer.addr(3, 2))

  for (i <- 0 until dataTable.length) {

    val rdEn = (dataRead && bankRdSel === i.U) || dataReplace
    val wrEn = (dataWrite && bankWrSel === i.U) ||
      (dataRefill && bankWrSel === i.U)
    dataTable(i).readwritePorts(0).address := Mux(wrEn, dataWrAddr, dataRdAddr)
    dataTable(i).readwritePorts(0).isWrite := wrEn
    dataTable(i).readwritePorts(0).enable  := rdEn || wrEn

    dataTable(i).readwritePorts(0).writeData := Mux(
      dataWrite,
      Fill(2, writeBuffer.wdata).asTypeOf(Vec(8, UInt(8.W))),
      mergedData
    )

    val mask = dataTable(i).readwritePorts(0).mask.get
    mask := dataWrMask.asTypeOf(Vec(8, Bool()))
  }

  /* --------------------------    lookup logic    ------------------------------- */

  val searchTag = reqBuffer.addr(31, 12)
  val tagv      = tagvTable.map { t =>
    t.readwritePorts(0).readData
  }
  val hitVec    = VecInit(tagv.map { data =>
    data.v && (data.tag === searchTag)
  })
  hit := hitVec.asUInt.orR && cacheable

  val data: Vec[Vec[UInt]] = VecInit(dataTable.map { t =>
    t.readwritePorts(0).readData
  })
  val raw64data = data(reqBuffer.addr(3, 2))
  hitWay := Mux(hitVec(0), 0.U, 1.U)

  /* --------------------------     age table       ------------------------------ */

  val ageTable = RegInit(VecInit(Seq.fill(256)(0.U(1.W))))

  val bufferIdx = reqBuffer.addr(11, 4)
  replaceWay := ageTable(bufferIdx)
  when(stateLookup && hit) {
    ageTable(bufferIdx) := ~hitWay
  }.elsewhen(stateRefill && refillCnt === 3.U && cacheable && io.rd.resp.fire) {
    ageTable(bufferIdx) := ~replaceWay
  }

  /* -------------------------   write back logic   ------------------------------ */

  val replaceData = data.map(inner => {
    val way0 = inner(3) ## inner(2) ## inner(1) ## inner(0)
    val way1 = inner(7) ## inner(6) ## inner(5) ## inner(4)
    Mux(replaceWay.asBool, way1, way0)
  })
  val replaceTag  = Mux(replaceWay.asBool, tagv(1), tagv(0))
  val replaceAddr = replaceTag.tag ## reqBuffer.addr(11, 4) ## 0.U(4.W)
  io.wr.bits.wdata := Mux(cacheable, Cat(replaceData.reverse), Fill(4, reqBuffer.wdata))
  io.wr.bits.waddr := Mux(cacheable, replaceAddr, reqBuffer.addr)
  io.wr.bits.wsize := Mux(cacheable, 3.U, 0.U)
  io.wr.bits.wstrb := Mux(cacheable, MASK_Z, reqBuffer.mask)

  io.wr.valid := stateReplace && (!replaceFlag || isUncachedWrite)

  /* --------------------------    IO logic    ----------------------------------- */

  val conflict1 =
    !io.cpu.req.bits.wen && io.cpu.req.valid &&
      stateWrite // &&
  // writeBuffer.addr(3, 2) === io.cpu.req.bits.addr(3, 2)

  val conflict2 =
    !io.cpu.req.bits.wen && io.cpu.req.valid &&
      isStore // &&
  // io.cpu.req.bits.addr(31, 2) === reqBuffer.addr(31, 2)

  io.cpu.req.ready := Mux1H(
    Seq(
      stateInvalid -> false.B,
      stateIdle    -> !conflict1,
      stateLookup  -> (hit && !conflict1 && !conflict2),
      stateMiss    -> false.B,
      stateReplace -> false.B,
      stateRefill  -> false.B
    )
  )

  val bankMatch = refillCnt === reqBuffer.addr(3, 2)
  io.cpu.resp.valid := Mux1H(
    Seq(
      stateInvalid -> false.B,
      stateIdle    -> false.B,
      stateLookup  -> hit,
      stateMiss    -> false.B,
      stateReplace -> false.B,
      stateRefill  -> ((io.rd.resp.valid && bankMatch) || isUncachedWrite)
    )
  )

  io.cpu.resp.bits.err := false.B // TODO

  val way0 = raw64data(3) ## raw64data(2) ## raw64data(1) ## raw64data(0)
  val way1 = raw64data(7) ## raw64data(6) ## raw64data(5) ## raw64data(4)

  io.cpu.resp.bits.rdata := Mux(stateRefill, io.rd.resp.bits.rdata, Mux(hitWay.asBool, way1, way0))

  io.rd.req.valid          := stateReplace && ~isUncachedWrite
  io.rd.req.bits.addr      := reqBuffer.addr(31, 4) ## 0.U(4.W)
  io.rd.req.bits.mask      := Mux(cacheable, MASK_ALL, reqBuffer.mask)
  io.rd.req.bits.size      := Mux(cacheable, 2.U, reqBuffer.size)
  io.rd.req.bits.wdata     := 0.U
  io.rd.req.bits.wen       := false.B
  io.rd.req.bits.cacheable := cacheable

  io.rd.resp.ready := stateRefill && ~(isStore && !cacheable)

}

class CacheWriteBuffer extends Module {
  val io = IO(new Bundle {
    val cache = Flipped(Decoupled(Input(new DCacheWrBundle)))
    val req   = Decoupled(Output(new SimpleMemReq(32)))
    val resp  = Flipped(Decoupled(Input(new SimpleMemResp(32))))
  })

  val sIdle :: sAddr :: sData :: sWaiting :: Nil = Enum(4)
  val state                                      = RegInit(sIdle)

  val stateIdle:    Bool = state === sIdle
  val stateAddr:    Bool = state === sAddr
  val stateData:    Bool = state === sData
  val stateWaiting: Bool = state === sWaiting

  val reqBuffer = RegInit(0.U.asTypeOf(new DCacheWrBundle))

  when(stateIdle) {
    when(io.cache.fire) {
      state     := sAddr
      reqBuffer := io.cache.bits
    }
  }

  val cacheable = !reqBuffer.wstrb.orR
  val idx       = RegInit(0.U(2.W))
  val finishReq = (idx === 3.U)

  when(stateAddr && io.req.fire) {
    idx := Mux(cacheable, 0.U, 3.U)
  }.elsewhen(stateData && io.req.fire) {
    idx := Mux(finishReq, 0.U, idx + 1.U)
  }

  when(stateAddr) {
    when(io.req.fire) {
      state := sData
    }
  }

  when(stateData) {
    when(finishReq && io.req.fire) {
      state := sWaiting
    }
  }

  when(stateWaiting) {
    when(io.resp.fire) {
      state := sIdle
    }
  }

  io.cache.ready        := stateIdle
  io.req.valid          := stateAddr || stateData
  io.req.bits.addr      := reqBuffer.waddr
  io.req.bits.cacheable := cacheable
  io.req.bits.wen       := true.B
  io.req.bits.mask      := Mux(cacheable, MASK_ALL, reqBuffer.wstrb)
  io.req.bits.size      := reqBuffer.wsize

  val cacheDataVec = reqBuffer.wdata.asTypeOf(Vec(4, UInt(32.W)))
  val cacheData    = cacheDataVec(idx)
  val uncacheData  = cacheDataVec(0)

  io.req.bits.wdata := Mux(cacheable, cacheData, uncacheData)

  io.resp.ready := stateWaiting

}
