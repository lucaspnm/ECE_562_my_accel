import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._

class WithReverbRoCC extends Config((site, here, up) => {
  case BuildRoCC => up(BuildRoCC, site) ++ Seq(
    (p: Parameters) => {
      val accel = LazyModule(new ReverbRoCC(OpcodeSet.custom0)(p))
      accel
    }
  )
})

class RocketWithReverbRoCCConfig extends Config(
  new WithReverbRoCC ++ new freechips.rocketchip.system.DefaultConfig)
