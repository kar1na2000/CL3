package cl3
import chisel3._
import chisel3.util._

case class AXI4Params(
    axiIdBits      : Int  = 4,
    axiAddrBits    : Int  = 32,  // [31:0]
    axiDataBits    : Int  = 32,  // W/R data [31:0]
    axiLenBits     : Int  = 8,   // [7:0]
    axiBurstBits   : Int  = 2,   // [1:0]
    axiRespBits    : Int  = 2,   // [1:0]
) {
    val axiStrbBits    : Int  = axiDataBits / 8
}

case class ICacheParams(
  axiIdDefault   : Int  = 0,

  // I-Cache request
  pcBits         : Int  = 32,
  instBits       : Int  = 64,  // req_inst_o is 64bit

  // Data RAM: addr[9:0], data[63:0]
  dataRamDataBits: Int  = 64,

  // Cache parameters
  numWays        : Int = 2,   // ICACHE_NUM_WAYS
  numLines       : Int = 256, // ICACHE_NUM_LINES
  lineSizeBytes  : Int = 32,  // ICACHE_LINE_SIZE
  dataBits       : Int = 64,  // ICACHE_DATA_W
  

  useMacro       : Boolean = true,
  axi: AXI4Params = AXI4Params()
  // macroVsrc      : String  = "/vsrc"
) {
  val lineSizeBits   : Int = log2Ceil(lineSizeBytes)   // ICACHE_LINE_SIZE_W
  val lineAddrBits   : Int = log2Ceil(numLines)      // ICACHE_LINE_ADDR_W

  val lineWords      : Int = lineSizeBytes / (axi.axiDataBits / 8) // ICACHE_LINE_WORDS

  val banks          : Int = lineSizeBytes / 8
  val bankSelBits    : Int = log2Ceil(banks)
  
  val tagCmpAddrBits : Int = pcBits - (lineAddrBits + lineSizeBits)  // ICACHE_TAG_CMP_ADDR_W
  val tagRamIdxBits  : Int  = lineAddrBits   // depth = 256
  val cacheTagValidBit : Int = tagCmpAddrBits // CACHE_TAG_VALID_BIT
  val cacheTagAddrBits : Int = tagCmpAddrBits - 1
  val cacheTagDataBits : Int = tagCmpAddrBits + 1 // (0-18) tag data; (19,19) valid
  val tagRamDataBits   : Int  = tagCmpAddrBits + 1 // need to delete

  val dataRamIdxBits : Int = log2Ceil(numLines)
  // Tag req bits
  val tagReqLineW: Int = 8
  val tagReqLineL: Int = 5
  val tagReqLineH: Int = 12

  // Tag compare bits
  val tagCmpAddrL    : Int = 13  // ICACHE_TAG_CMP_ADDR_L
  val tagCmpAddrH    : Int = 31  // ICACHE_TAG_CMP_ADDR_H
}

case class DCacheParams(
    addrW:   Int = 32,
    dataW:   Int = 32,
    idW:     Int = 4,
    reqTagW: Int = 11,    // mem_req_tag
    axi_id : Int = 0,

    ways:       Int = 2,      // DCACHE_NUM_WAYS
    sets:       Int = 256,    // DCACHE_NUM_LINES
    lineBytes:  Int = 32,     // 32B
    lenW:       Int = 8,      // AXI burst len
    burstW:     Int = 2,      // AXI burst width
    tagMetaBits:Int = 2,      // {dirty, valid}
    
    dataRamDataBits: Int  = 32,

    // sram
    useMacro       : Boolean = true,
    axi: AXI4Params = AXI4Params()
    // macroVsrc      : String  = "/vsrc"
) {

  // DCACHE_LINE_SIZE_W = log2(32) = 5
  val offBits     = log2Ceil(lineBytes)           // = 5  (DCACHE_LINE_SIZE_W)
  // DCACHE_LINE_ADDR_W = log2(256) = 8
  val idxBits     = log2Ceil(sets)                // = 8  (DCACHE_LINE_ADDR_W)

  val beatBytes   = dataW / 8                     // = 4
  val wstrbW      = beatBytes                     // = 4

  val lineWords   = lineBytes / beatBytes         // = 8  (DCACHE_LINE_WORDS)

  val banks          : Int = lineBytes / 8
  val bankSelBits    : Int = log2Ceil(banks)

  // Tag bits: 32 - 5 - 8 = 19
  val tagBits     = addrW - offBits - idxBits     // = 19 (CACHE_TAG_ADDR_BITS)
  // tagBits + {dirty,valid} = 19 + 2 = 21
  val tagEntryW   = tagBits + tagMetaBits         // = 21 (CACHE_TAG_DATA_W)

  // DCACHE_TAG_REQ_LINE_L/H/W = 5 / 12 / 8
  val tagReqLineL = offBits                       // = 5  (DCACHE_TAG_REQ_LINE_L)
  val tagReqLineH = offBits + idxBits - 1         // = 12 (DCACHE_TAG_REQ_LINE_H)
  val tagReqLineW = idxBits                       // = 8  (DCACHE_TAG_REQ_LINE_W)

  // DCACHE_TAG_CMP_ADDR_L = DCACHE_TAG_REQ_LINE_H + 1 = 13
  // DCACHE_TAG_CMP_ADDR_H = 31
  // DCACHE_TAG_CMP_ADDR_W = 31 - 13 + 1 = 19
  val tagCmpAddrL = tagReqLineH + 1               // = 13
  val tagCmpAddrH = addrW - 1                     // = 31
  val tagCmpAddrW = tagCmpAddrH - tagCmpAddrL + 1 // = 19

  // `CACHE_TAG_ADDR_RNG` = 18:0
  val cacheTagAddrBits = tagBits                  // = 19 (CACHE_TAG_ADDR_BITS)
  val cacheTagAddrH    = cacheTagAddrBits - 1     // = 18
  val cacheTagAddrL    = 0                        // = 0


  val tagDirtyBit = cacheTagAddrBits + 0     // = 19
  val tagValidBit = cacheTagAddrBits + 1     // = 20
  val tagDataW    = cacheTagAddrBits + 2     // = 21

  // ========= Data RAM 地址位宽（与之前 11 位保持一致） =========
  // 32 位字寻址：offBits(5) - log2(4)=2 -> 行内 word 索引 3 位
  // 再加上 idxBits(8) -> 8 + 3 = 11
  val dataIdxW    = idxBits + (offBits - log2Ceil(beatBytes)) // = 11

  val tagIdxW     = idxBits                                   // = 8
}

