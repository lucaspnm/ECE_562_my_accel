package reverbrocc

import freechips.rocketchip.tile._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import chisel3._

class ReverbRoCC(opcodes: OpcodeSet)(implicit p: Parameters)
  extends LazyRoCC(opcodes, nPTWPorts = 0) {
  override lazy val module = new ReverbRoCCModule(this)
}

class ReverbRoCCModule(outer: ReverbRoCC)(implicit p: Parameters)
  extends LazyRoCCModuleImp(outer) {
  io.resp.valid := false.B
  io.busy := false.B
  io.interrupt := false.B
  io.mem.req.valid := false.B
  io.mem.req.bits := DontCare
  when (io.cmd.fire()) {
    printf("[ReverbRoCC] got command: funct=%x rs1=%x rs2=%x\n",
      io.cmd.bits.inst.funct, io.cmd.bits.rs1, io.cmd.bits.rs2)
  }
}
