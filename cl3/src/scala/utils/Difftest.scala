package cl3

import chisel3._
import chisel3.util._

class DiffInfo extends Bundle {
  val pc     = UInt(32.W)
  val inst   = UInt(32.W)
  val commit = Bool()
  val skip   = Bool()
}
class Difftest extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {

    val clock     = Input(Clock())
    val reset     = Input(Reset())
    val diff      = Input(Vec(2, new DiffInfo()))
  })

  override def desiredName: String = "difftest_wrapper"
  dontTouch(io)

  addResource("/vsrc/difftest_info_pkg.sv")
  addResource("/vsrc/difftest_wrapper.sv")
  addResource("/vsrc/difftest.sv")


}

