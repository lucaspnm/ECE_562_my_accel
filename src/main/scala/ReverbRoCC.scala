// File: src/main/scala/ReverbRoCC.scala
package reverbrocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import freechips.rocketchip.tile._           // LazyRoCC, OpcodeSet
import freechips.rocketchip.diplomacy._      // LazyModule

/** Top-level LazyRoCC for the reverb accelerator (smoke version). */
class ReverbRoCC(opcodes: OpcodeSet)(implicit p: Parameters)
  extends LazyRoCC(opcodes, nPTWPorts = 0) {
  override lazy val module = new ReverbRoCCModule(this)
}

/** Minimal module implementation:
  * - Always ready to accept commands
  * - Prints the incoming command
  * - Returns rs1 + rs2 as the response (rd chosen by the instruction)
  */
class ReverbRoCCModule(outer: ReverbRoCC)(implicit p: Parameters)
  extends LazyRoCCModuleImp(outer) {

  // Default-safe wiring
  io.cmd.ready      := true.B            // accept commands immediately
  io.busy           := false.B           // never stall Rocket (for this smoke test)
  io.interrupt      := false.B

  // Not using memory/TL yet
  io.mem.req.valid  := false.B
  io.mem.req.bits   := DontCare

  // Default: no response unless we fire below
  io.resp.valid     := false.B
  io.resp.bits.rd   := 0.U
  io.resp.bits.data := 0.U

  when (io.cmd.fire()) {
    // Helpful log in Verilator console
    printf(p"[ReverbRoCC] got command: funct=${Hexadecimal(io.cmd.bits.inst.funct)} " +
           p"rs1=${Hexadecimal(io.cmd.bits.rs1)} rs2=${Hexadecimal(io.cmd.bits.rs2)}\n")

    // Return rs1 + rs2 to the destination register specified by the instruction
    io.resp.valid     := true.B
    io.resp.bits.rd   := io.cmd.bits.inst.rd
    io.resp.bits.data := io.cmd.bits.rs1 + io.cmd.bits.rs2
  }
}
