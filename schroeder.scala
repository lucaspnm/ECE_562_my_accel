package chipyard

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Config, Parameters}
import freechips.rocketchip.tile._
import freechips.rocketchip.diplomacy._


// RoCC Accelerator: Schroeder Reverberator
// Optimized for visually compelling impulse response plots.
class SchroederRoCC(opcodes: OpcodeSet)(implicit p: Parameters)
  extends LazyRoCC(opcodes) {
  override lazy val module = new SchroederRoCCModuleImp(this)
}

class SchroederRoCCModuleImp(outer: SchroederRoCC)(implicit p: Parameters)
  extends LazyRoCCModuleImp(outer)
{
  
  // Parameters (shorter delays for stronger early response)
  val maxDelay   = 512
  val delayWidth = log2Ceil(maxDelay).W
  val combCount  = 4
  val apCount    = 2

  
  // RoCC interface
  val cmd   = io.cmd
  val funct = cmd.bits.inst.funct
  val rs1   = cmd.bits.rs1
  val rs2   = cmd.bits.rs2
  cmd.ready := true.B

  val FUNCT_WRITE_SAMPLE = 0.U
  val FUNCT_STEP         = 1.U
  val FUNCT_READ_SAMPLE  = 2.U
  val FUNCT_LOAD_COMB_G  = 3.U
  val FUNCT_LOAD_AP_G    = 4.U
  val FUNCT_LOAD_WET     = 5.U
  val FUNCT_RESET        = 6.U

  
  // Helpers
  def sat16(x: SInt): SInt = {
    val max = 32767.S(16.W)
    val min = (-32768).S(16.W)
    Mux(x > max, max,
      Mux(x < min, min, x(15,0).asSInt))
  }

  
  // Early reflection buffer (tiny FIR)
  val earlyDelay = 64
  val earlyBuf = RegInit(VecInit(Seq.fill(earlyDelay)(0.S(16.W))))
  val earlyPtr = RegInit(0.U(log2Ceil(earlyDelay).W))

  
  // Comb delay lines (shorter for early energy)
  val combBuf = Seq.fill(combCount)(
    RegInit(VecInit(Seq.fill(maxDelay)(0.S(16.W))))
  )
  val combPtr = RegInit(VecInit(Seq.fill(combCount)(0.U(delayWidth))))

  // comb delays (strong early buildup)
  val combDelays = RegInit(VecInit(Seq(
    37.U(delayWidth),
    61.U(delayWidth),
    89.U(delayWidth),
    113.U(delayWidth)
  )))

  val combG = RegInit(VecInit(Seq(
    31800.S(16.W),  // strong feedback
    31000.S(16.W),
    30000.S(16.W),
    29500.S(16.W)
  )))

  
  // Allpass filters (with high diffusion)
  
  val apInBuf = Seq.fill(apCount)(RegInit(VecInit(Seq.fill(maxDelay)(0.S(16.W)))))
  val apOutBuf = Seq.fill(apCount)(RegInit(VecInit(Seq.fill(maxDelay)(0.S(16.W)))))
  val apPtr = RegInit(VecInit(Seq.fill(apCount)(0.U(delayWidth))))

  val apDelays = RegInit(VecInit(Seq(
    43.U(delayWidth),
    67.U(delayWidth)
  )))

  val apG = RegInit(VecInit(Seq(
    30000.S(16.W),   // ~0.92
    28500.S(16.W)    // ~0.87
  )))

  
  // Wet Mix
  val wet = RegInit(28000.S(16.W)) // ~0.85 wet for nice looking IR

  
  // Input + Output
  val inSample  = RegInit(0.S(16.W))
  val outSample = RegInit(0.S(16.W))

  
  // Decode functs
  val doWrite = funct === FUNCT_WRITE_SAMPLE
  val doStep  = funct === FUNCT_STEP
  val doRead  = funct === FUNCT_READ_SAMPLE
  val doLoadCombG = funct === FUNCT_LOAD_COMB_G
  val doLoadApG   = funct === FUNCT_LOAD_AP_G
  val doLoadWet   = funct === FUNCT_LOAD_WET
  val doReset     = funct === FUNCT_RESET


  // Write input sample
  when (cmd.fire && doWrite) {
    inSample := rs1(15,0).asSInt
  }

  // Load coefficients
  when (cmd.fire && doLoadCombG) { combG(rs2(1,0)) := rs1(15,0).asSInt }
  when (cmd.fire && doLoadApG)   { apG(rs2(0))     := rs1(15,0).asSInt }
  when (cmd.fire && doLoadWet)   { wet             := rs1(15,0).asSInt }

  // Reset everything
  when (cmd.fire && doReset) {
    for (i <- 0 until combCount) {
      for (j <- 0 until maxDelay) combBuf(i)(j) := 0.S
      combPtr(i) := 0.U
    }
    for (i <- 0 until apCount) {
      for (j <- 0 until maxDelay) {
        apInBuf(i)(j)  := 0.S
        apOutBuf(i)(j) := 0.S
      }
      apPtr(i) := 0.U
    }
    for (j <- 0 until earlyDelay) earlyBuf(j) := 0.S
    earlyPtr := 0.U
    inSample := 0.S
    outSample := 0.S
  }

  // CIRCUIT FOR ONE STEP (funct=1)
  when (cmd.fire && doStep) {

    // EARLY REFLECTION FIR
    val d7  = earlyBuf((earlyPtr + (earlyDelay.U - 7.U)) % earlyDelay.U)
    val d12 = earlyBuf((earlyPtr + (earlyDelay.U - 12.U)) % earlyDelay.U)
    val d40 = earlyBuf((earlyPtr + (earlyDelay.U - 40.U)) % earlyDelay.U)

    val early =
      (inSample.asSInt) +
      ((d7  * 13107.S) >> 15).asSInt +   // 0.4
      ((d12 * 9830.S)  >> 15).asSInt +   // 0.3
      ((d40 * 6553.S)  >> 15).asSInt     // 0.2

    // write into early circular buffer
    earlyBuf(earlyPtr) := inSample
    earlyPtr := Mux(earlyPtr === (earlyDelay-1).U, 0.U, earlyPtr + 1.U)

    // COMB FILTERS (now fed by early reflections)
    val combOuts = Wire(Vec(combCount, SInt(32.W)))

    for (i <- 0 until combCount) {
      val d = combDelays(i)
      val rdIdx = Mux(combPtr(i) >= d, combPtr(i) - d, combPtr(i) + maxDelay.U - d)
      val delayed = combBuf(i)(rdIdx)

      val fb = (delayed * combG(i) >> 15).asSInt
      val y = early + fb

      combOuts(i) := y

      combBuf(i)(combPtr(i)) := early
      combPtr(i) := Mux(combPtr(i) === (maxDelay-1).U, 0.U, combPtr(i) + 1.U)
    }

    val combSum = (combOuts.reduce(_ +& _) >> 1).asSInt  // reduce amplitude

    
    // ALLPASS CHAIN (strong diffusion)
    var apSignal: SInt = combSum

    for (i <- 0 until apCount) {
      val d = apDelays(i)
      val rdIdx = Mux(apPtr(i) >= d, apPtr(i) - d, apPtr(i) + maxDelay.U - d)

      val xD = apInBuf(i)(rdIdx)
      val yD = apOutBuf(i)(rdIdx)

      val xNow = sat16(apSignal)

      val t1 = ((-apG(i)).asSInt * xNow >> 15).asSInt
      val t2 = ((yD * apG(i)) >> 15).asSInt

      val y = t1 + t2 + xD.asSInt
      val y16 = sat16(y)

      apInBuf(i)(apPtr(i))  := xNow
      apOutBuf(i)(apPtr(i)) := y16
      apPtr(i) := Mux(apPtr(i)===(maxDelay-1).U, 0.U, apPtr(i)+1.U)

      apSignal = y
    }

  
    // WET MIX
    val final16 = sat16(((apSignal * wet) >> 15).asSInt)
    outSample := final16
  }

  // RESPONSES
  io.resp.valid     := cmd.valid && doRead
  io.resp.bits.rd   := cmd.bits.inst.rd
  io.resp.bits.data := outSample.asUInt
  io.busy           := false.B
  io.interrupt      := false.B
}


// Config
class WithSchroederRoCC(op: OpcodeSet = OpcodeSet.custom0)
  extends Config((site, here, up) => {
    case BuildRoCC =>
      up(BuildRoCC) :+
        { p: Parameters => LazyModule(new SchroederRoCC(op)(p)) }
  })

class SchroederConfig extends Config(
   new WithSchroederRoCC ++
   new freechips.rocketchip.rocket.WithNBigCores(1) ++
   new chipyard.config.AbstractConfig
)


