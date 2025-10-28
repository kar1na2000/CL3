package cl3

import chisel3._
import chisel3.util._

class Axi4Aw(p: AXI4Params) extends Bundle {
  val addr  = UInt(p.axiAddrBits.W)
  val id    = UInt(p.axiIdBits.W)
  val len   = UInt(p.axiLenBits.W)
  val burst = UInt(p.axiBurstBits.W)

  val size  = UInt(3.W) 
  val lock  = UInt(1.W)
  val cache = UInt(4.W)
  val prot  = UInt(3.W)

  // val qos   = UInt(4.W)
  // val region= UInt(4.W)
  // val user  = UInt(4.W)

}

class Axi4W(p: AXI4Params) extends Bundle {
  val data  = UInt(p.axiDataBits.W)
  val strb  = UInt(p.axiStrbBits.W)
  val last  = Bool()
  // val user  = UInt(4.W)

}

class Axi4B(p: AXI4Params) extends Bundle {
  val resp  = UInt(p.axiRespBits.W)
  val id    = UInt(p.axiIdBits.W)
  // val user  = UInt(4.W)
}

class Axi4Ar(p: AXI4Params) extends Bundle {
  val addr  = UInt(p.axiAddrBits.W)
  val id    = UInt(p.axiIdBits.W)
  val len   = UInt(p.axiLenBits.W)
  val burst = UInt(p.axiBurstBits.W)

  val size  = UInt(3.W) 
  val lock  = UInt(1.W)
  val cache = UInt(4.W)
  val prot  = UInt(3.W)

  // val qos   = UInt(4.W)
  // val region= UInt(4.W)
  // val user  = UInt(4.W)
}

class Axi4R(p: AXI4Params) extends Bundle {
  val data  = UInt(p.axiDataBits.W)
  val resp  = UInt(p.axiRespBits.W)
  val id    = UInt(p.axiIdBits.W)
  val last  = Bool()
  // val user  = UInt(4.W)
}

class Axi4MasterIO(p: AXI4Params) extends Bundle {
  val aw = Decoupled(new Axi4Aw(p))            // master -> slave
  val w  = Decoupled(new Axi4W(p))             // master -> slave
  val b  = Flipped(Decoupled(new Axi4B(p)))    // slave  -> master
  val ar  = Decoupled(new Axi4Ar(p))           // master -> slave
  val r   = Flipped(Decoupled(new Axi4R(p)))   // slave  -> master
}

class Axi4SlaveIO(p: AXI4Params) extends Bundle {
  val aw = Flipped(Decoupled(new Axi4Aw(p)))
  val w  = Flipped(Decoupled(new Axi4W(p)))
  val b  = Decoupled(new Axi4B(p))
  val ar = Flipped(Decoupled(new Axi4Ar(p)))
  val r  = Decoupled(new Axi4R(p))
}