package cl3

import chisel3._
import chisel3.util._

class dcache_pmem_mux(p: DCacheParams) extends Module {
    val io = IO(new DCachePmemMuxIO(p))

    val (outreq, outresp, in0req, in0resp, in1req, in1resp) = (io.outport.req, io.outport.resp, io.in0port.req, io.in0port.resp, io.in1port.req, io.in1port.resp)

    val select_q             = RegInit(false.B)
    val outport_wr_r         = Mux(select_q, in1req.wr,        in0req.wr)
    val outport_rd_r         = Mux(select_q, in1req.rd,        in0req.rd)
    val outport_len_r        = Mux(select_q, in1req.len,       in0req.len)
    val outport_addr_r       = Mux(select_q, in1req.addr,      in0req.addr)
    val outport_write_data_r = Mux(select_q, in1req.writeData, in0req.writeData)


    // outport_wr_r         := Mux(select_q, in1req.wr,        in0req.wr)
    // outport_rd_r         := Mux(select_q, in1req.rd,        in0req.rd)
    // outport_len_r        := Mux(select_q, in1req.len,       in0req.len)
    // outport_addr_r       := Mux(select_q, in1req.addr,      in0req.addr)
    // outport_write_data_r := Mux(select_q, in1req.writeData, in0req.writeData)

    outreq.wr        := outport_wr_r        
    outreq.rd        := outport_rd_r        
    outreq.len       := outport_len_r       
    outreq.addr      := outport_addr_r      
    outreq.writeData := outport_write_data_r

    select_q := io.select_i

    in0resp.ack      := (select_q === false.B)    && outresp.ack      
    in0resp.error    := (select_q === false.B)    && outresp.error    
    in1resp.ack      := (select_q === true.B)     && outresp.ack      
    in1resp.error    := (select_q === true.B)     && outresp.error    

    in0resp.accept   := (io.select_i === false.B) && outresp.accept   
    in1resp.accept   := (io.select_i === true.B)  && outresp.accept   

    in0resp.readData := outresp.readData
    in1resp.readData := outresp.readData


}