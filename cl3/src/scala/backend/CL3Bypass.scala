package cl3

import chisel3._
import chisel3.util._

class BypassInput extends Bundle {
  val issue = Input(Vec(2, new BypassISInfo))
  val pipe  = Input(Vec(2, new PipeOutput))
}

class BypassOutput extends Bundle {

  val ra0 = Output(UInt(32.W))
  val rb0 = Output(UInt(32.W))
  val ra1 = Output(UInt(32.W))
  val rb1 = Output(UInt(32.W))

  val debug = Output(Bool())

}

class BypassIO extends Bundle {
  val in  = new BypassInput
  val out = new BypassOutput
}

class BypassNetwork extends Module {
  val io = IO(new BypassIO)

  val pipe0 = io.in.pipe(0)
  val pipe1 = io.in.pipe(1)

  val issue0 = io.in.issue(0)
  val issue1 = io.in.issue(1)

  val bypassSources = Seq(
    (pipe1.e1.isALU || pipe1.e1.isJmp, pipe1.e1.rdIdx, pipe1.e1.result),
    (pipe0.e1.isALU || pipe0.e1.isJmp, pipe0.e1.rdIdx, pipe0.e1.result),
    (pipe1.e2.valid && pipe1.e2.info.wen, pipe1.e2.rdIdx, pipe1.e2.result),
    (pipe0.e2.valid && pipe0.e2.info.wen, pipe0.e2.rdIdx, pipe0.e2.result),
    (pipe1.wb.commit && pipe1.wb.info.wen, pipe1.wb.rdIdx, pipe1.wb.result),
    (pipe0.wb.commit && pipe0.wb.info.wen, pipe0.wb.rdIdx, pipe0.wb.result)
  )

  io.out.ra0 := BypassNetwork(issue0.raIdx, issue0.ra, bypassSources)
  io.out.rb0 := BypassNetwork(issue0.rbIdx, issue0.rb, bypassSources)
  io.out.ra1 := BypassNetwork(issue1.raIdx, issue1.ra, bypassSources)
  io.out.rb1 := BypassNetwork(issue1.rbIdx, issue1.rb, bypassSources)

  io.out.debug := (pipe1.e1.isALU || pipe1.e1.isJmp) && pipe1.e1.rdIdx.orR && pipe1.e1.rdIdx === issue0.raIdx

}

object BypassNetwork {
  def apply(rsIdx: UInt, defaultValue: UInt, sources: Seq[(Bool, UInt, UInt)]): UInt = {
    PriorityMux(
      sources.map { case (condition, rdIdx, data) =>
        val bypass_hit = condition && (rdIdx =/= 0.U) && (rdIdx === rsIdx)
        (bypass_hit, data)
      } :+
        (true.B, defaultValue)
    )
  }
}
