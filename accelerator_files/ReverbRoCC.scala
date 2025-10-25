package reverb

import chisel3._
import chisel3.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.rocket._
import freechips.rocketchip.diplomacy._

/** Simple Reverb Accelerator using RoCC interface */
class ReverbRoCC(opcode: Int = 0)(implicit p: Parameters) extends LazyRoCC(opcode = OpcodeSet.custom0) {
  override lazy val module = new ReverbRoCCModule(this)
}

class ReverbRoCCModule(outer: ReverbRoCC)(implicit p: Parameters) extends LazyRoCCModuleImp(outer) {
  // Command interface
  val cmd = io.cmd
  val resp = io.resp
  val busy = RegInit(false.B)

  // Default values
  io.resp.valid := false.B
  io.resp.bits.rd := cmd.bits.inst.rd
  io.resp.bits.data := 0.U
  io.interrupt := false.B
  cmd.ready := !busy

  // Registers for delay lines (tiny reverb example)
  val delayBuffer = Reg(Vec(4, SInt(16.W))) // small feedback delay line
  val writeIdx = RegInit(0.U(2.W))
  val gain = 0.7.S(16.W) // feedback coefficient

  val inputSample = cmd.bits.rs1.asSInt
  val outputSample = Wire(SInt(16.W))

  // Simple feedback delay network (tiny Schroeder-style)
  val delayed = delayBuffer(writeIdx)
  val mixed = inputSample + (delayed * gain >> 8).asSInt

  // Write new sample into buffer
  delayBuffer(writeIdx) := mixed
  writeIdx := writeIdx + 1.U

  // All-pass filter approximation (simple one-stage)
  val apGain = 0.5.S(16.W)
  val apOut = delayed - (apGain * mixed >> 8).asSInt
  val processed = apOut + (apGain * delayed >> 8).asSInt

  outputSample := processed

  // Respond logic
  when(cmd.fire()) {
    io.resp.valid := true.B
    io.resp.bits.data := outputSample.asUInt
  }
}
