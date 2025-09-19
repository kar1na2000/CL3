import cl3._

object Elaborate extends App {
  val firtoolOptions = Array(
    "--lowering-options=" + List(
      // make yosys happy
      // see https://github.com/llvm/circt/blob/main/docs/VerilogGeneration.md
      "disallowLocalVariables",
      "disallowPackedArrays",
      "locationInfoStyle=wrapInAtSquareBracket"
    ).reduce(_ + "," + _),
    "--disable-all-randomization"
    // "-o=vsrc/sv-gen",
    // "--split-verilog"
  )
  circt.stage.ChiselStage.emitSystemVerilogFile(new CL3Decode(), args, firtoolOptions)
}
