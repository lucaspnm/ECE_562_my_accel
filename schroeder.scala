package chipyard

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Config, Parameters}
import freechips.rocketchip.tile._
import freechips.rocketchip.diplomacy._

// ------------------------------------------------------------
// Schroeder Reverb RoCC (multi-function)
// funct=0: write input sample (Q1.15 in rs1[15:0])
// funct=1: step (process one sample)
// funct=2: read output sample (in rd)
// funct=3: load comb gain   (rs1=Q1.15, rs2[1:0]=index)
// funct=4: load allpass gain(rs1=Q1.15, rs2[0]=index)
// funct=5: load wet mix     (rs1=Q1.15)
// funct=6: reset internal state
// ------------------------------------------------------------
class SchroederRoCC(opcodes: OpcodeSet)(implicit p: Parameters)
  extends LazyRoCC(opcodes) {
  override lazy val module = new SchroederRoCCModuleImp(this)
}

class SchroederRoCCModuleImp(outer: SchroederRoCC)(implicit p: Parameters)
  extends LazyRoCCModuleImp(outer) with HasCoreParameters {

  // ---------------- Parameters ----------------
  val maxDelay   = 512
  val delayWidth = log2Ceil(maxDelay).W
  val combCount  = 4
  val apCount    = 2
  val earlyDelay = 64

  // ---------------- RoCC interface ----------------
  val cmd   = Queue(io.cmd)   // 1-element queue to break comb path
  val funct = cmd.bits.inst.funct
  val rs1   = cmd.bits.rs1
  val rs2   = cmd.bits.rs2

  val FUNCT_WRITE_SAMPLE = 0.U
  val FUNCT_STEP         = 1.U
  val FUNCT_READ_SAMPLE  = 2.U
  val FUNCT_LOAD_COMB_G  = 3.U
  val FUNCT_LOAD_AP_G    = 4.U
  val FUNCT_LOAD_WET     = 5.U
  val FUNCT_RESET        = 6.U

  // Which operation?
  val doWrite     = funct === FUNCT_WRITE_SAMPLE
  val doStep      = funct === FUNCT_STEP
  val doRead      = funct === FUNCT_READ_SAMPLE
  val doLoadCombG = funct === FUNCT_LOAD_COMB_G
  val doLoadApG   = funct === FUNCT_LOAD_AP_G
  val doLoadWet   = funct === FUNCT_LOAD_WET
  val doReset     = funct === FUNCT_RESET

  val needsResp   = doRead && cmd.bits.inst.xd

  // ---------------- Helpers ----------------
  def sat16(x: SInt): SInt = {
    val max = 32767.S(16.W)
    val min = (-32768).S(16.W)
    Mux(x > max, max, Mux(x < min, min, x))
  }

  def circIdx(ptr: UInt, delay: Int, size: Int): UInt = {
    val d = delay.U
    val s = size.U
    Mux(ptr >= d, ptr - d, ptr + (s - d))
  }

  // ---------------- Early reflection buffer ----------------
  val earlyBuf = RegInit(VecInit(Seq.fill(earlyDelay)(0.S(16.W))))
  val earlyPtr = RegInit(0.U(log2Ceil(earlyDelay).W))

  // ---------------- Comb delay lines ----------------
  val combBuf = Seq.fill(combCount)(
    RegInit(VecInit(Seq.fill(maxDelay)(0.S(16.W))))
  )
  val combPtr = RegInit(VecInit(Seq.fill(combCount)(0.U(delayWidth))))

  val combDelays = RegInit(VecInit(Seq(
    37.U(delayWidth),
    61.U(delayWidth),
    89.U(delayWidth),
    113.U(delayWidth)
  )))

  val combG = RegInit(VecInit(Seq(
    31800.S(16.W),
    31000.S(16.W),
    30000.S(16.W),
    29500.S(16.W)
  )))

  // ---------------- Allpass filters ----------------
  val apInBuf  = Seq.fill(apCount)(RegInit(VecInit(Seq.fill(maxDelay)(0.S(16.W)))))
  val apOutBuf = Seq.fill(apCount)(RegInit(VecInit(Seq.fill(maxDelay)(0.S(16.W)))))
  val apPtr    = RegInit(VecInit(Seq.fill(apCount)(0.U(delayWidth))))

  val apDelays = RegInit(VecInit(Seq(
    43.U(delayWidth),
    67.U(delayWidth)
  )))

  val apG = RegInit(VecInit(Seq(
    30000.S(16.W),
    28500.S(16.W)
  )))

  // ---------------- Wet mix & IO samples ----------------
  val wet       = RegInit(28000.S(16.W)) // ~0.85
  val inSample  = RegInit(0.S(16.W))
  val outSample = RegInit(0.S(16.W))

  // ---------------- Coefficient loads ----------------
  when (cmd.fire && doLoadCombG) {
    val idx = rs2(1,0) // 0..3
    when (idx < combCount.U) {
      combG(idx) := rs1(15,0).asSInt
    }
  }

  when (cmd.fire && doLoadApG) {
    val idx = rs2(0) // 0..1
    when (idx < apCount.U) {
      apG(idx) := rs1(15,0).asSInt
    }
  }

  when (cmd.fire && doLoadWet) {
    wet := rs1(15,0).asSInt
  }

  // ---------------- Write input sample ----------------
  when (cmd.fire && doWrite) {
    inSample := rs1(15,0).asSInt
  }

  // ---------------- Reset internal state ----------------
  when (cmd.fire && doReset) {
    for (j <- 0 until earlyDelay) {
      earlyBuf(j) := 0.S
    }
    earlyPtr := 0.U
    for (i <- 0 until combCount) {
      for (j <- 0 until maxDelay) {
        combBuf(i)(j) := 0.S
      }
      combPtr(i) := 0.U
    }
    for (i <- 0 until apCount) {
      for (j <- 0 until maxDelay) {
        apInBuf(i)(j)  := 0.S
        apOutBuf(i)(j) := 0.S
      }
      apPtr(i) := 0.U
    }
    inSample  := 0.S
    outSample := 0.S
  }

  // ---------------- One STEP operation ----------------
  when (cmd.fire && doStep) {
    // Early reflections: taps at 0,7,12,40 samples
    val d7  = earlyBuf(circIdx(earlyPtr, 7,  earlyDelay))
    val d12 = earlyBuf(circIdx(earlyPtr, 12, earlyDelay))
    val d40 = earlyBuf(circIdx(earlyPtr, 40, earlyDelay))

    val early: SInt =
      inSample +
      ((d7  * 13107.S(16.W)) >> 15).asSInt +  // 0.4
      ((d12 * 9830.S(16.W))  >> 15).asSInt +  // 0.3
      ((d40 * 6553.S(16.W))  >> 15).asSInt    // 0.2

    // write input into early buffer
    earlyBuf(earlyPtr) := inSample
    earlyPtr := Mux(earlyPtr === (earlyDelay-1).U, 0.U, earlyPtr + 1.U)

    // 4 feedback combs, fed by "early"
    val combOuts = Wire(Vec(combCount, SInt(32.W)))
    for (i <- 0 until combCount) {
      val d = combDelays(i)
      val rdIdx = Mux(combPtr(i) >= d, combPtr(i) - d, combPtr(i) + maxDelay.U - d)
      val delayed = combBuf(i)(rdIdx)
      val fb = (delayed * combG(i) >> 15).asSInt
      val y  = early + fb

      combOuts(i) := y
      combBuf(i)(combPtr(i)) := y // store output for feedback
      combPtr(i) := Mux(combPtr(i) === (maxDelay-1).U, 0.U, combPtr(i) + 1.U)
    }

    val combSum = (combOuts.reduce(_ +& _) >> 1).asSInt  // scale down

    // Allpass chain
    var apSignal: SInt = combSum

    for (i <- 0 until apCount) {
      val d = apDelays(i)
      val rdIdx = Mux(apPtr(i) >= d, apPtr(i) - d, apPtr(i) + maxDelay.U - d)

      val xD = apInBuf(i)(rdIdx)
      val yD = apOutBuf(i)(rdIdx)

      val xNow = sat16(apSignal)

      val t1 = ((-apG(i)) * xNow >> 15).asSInt
      val t2 = (yD * apG(i) >> 15).asSInt

      val y   = t1 + t2 + xD
      val y16 = sat16(y)

      apInBuf(i)(apPtr(i))  := xNow
      apOutBuf(i)(apPtr(i)) := y16
      apPtr(i) := Mux(apPtr(i) === (maxDelay-1).U, 0.U, apPtr(i) + 1.U)

      apSignal = y16
    }

    val final16 = sat16((apSignal * wet >> 15).asSInt)
    outSample := final16
  }

  // ---------------- Responses & control ----------------
  // Default TL interface unused
  io.mem.req.valid := false.B
  io.mem.req.bits  := DontCare

  // Simple “busy” definition: busy while a command is valid and not yet consumed
  io.busy      := cmd.valid && !cmd.ready
  io.interrupt := false.B

  // Response only on READ_SAMPLE + xd=1
  io.resp.valid     := cmd.valid && needsResp
  io.resp.bits.rd   := cmd.bits.inst.rd
  io.resp.bits.data := outSample.asSInt.pad(xLen).asUInt

  // Back-pressure: if core can't take resp, don't accept next cmd
  cmd.ready := !needsResp || io.resp.ready
}

// Config hook
class WithSchroederRoCC(op: OpcodeSet = OpcodeSet.custom0)
  extends Config((site, here, up) => {
    case BuildRoCC =>
      up(BuildRoCC) :+ { p: Parameters =>
        LazyModule(new SchroederRoCC(op)(p))
      }
  })

class SchroederT extends Config(
  new WithSchroederRoCC ++
  new freechips.rocketchip.rocket.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig
)


