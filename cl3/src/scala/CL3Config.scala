package cl3

trait CL3Config {
  val addrWidth = 32
  val dataWidth = 32
  val EnableMMU = true
  val EnableBP  = true
}

object CL3Config extends CL3Config {}
