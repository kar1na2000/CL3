// // import icache._
// import cache._
// import chisel3._

// object Elaborate extends App {
//   val outDir = "./build"
//   val firtoolOptions = Array(
//     "--lowering-options=" + List(
//       // make yosys happy
//       // see https://github.com/llvm/circt/blob/main/docs/VerilogGeneration.md
//       "disallowLocalVariables",
//       "disallowPackedArrays",
//       "locationInfoStyle=wrapInAtSquareBracket"
//     ).reduce(_ + "," + _)
//   )
//   implicit val axiP: AXI4Params = AXI4Params()
//   implicit val dp: DCacheParams = DCacheParams()
//   implicit val ip: ICacheParams = ICacheParams()
//   // circt.stage.ChiselStage.emitSystemVerilogFile(new cache.DCache(DCacheParams()), args, firtoolOptions)
//   // circt.stage.ChiselStage.emitSystemVerilogFile(new cache.ICache(ICacheParams()), args, firtoolOptions)
//   // circt.stage.ChiselStage.emitSystemVerilogFile(new cache.AxiArbiter(), args, firtoolOptions)
//   circt.stage.ChiselStage.emitSystemVerilogFile(new cache.CacheSubSystem(), args, firtoolOptions)

// }
