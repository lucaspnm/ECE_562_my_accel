package chipyard

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import reverbrocc._

class WithReverbRoCC extends Config((site, here, up) => {
  case BuildRoCC => up(BuildRoCC, site) ++ Seq( { p: Parameters =>
    LazyModule(new ReverbRoCC(OpcodeSet.custom0)(p))
  })
})

class RocketWithReverbRoCCConfig extends Config(
  new WithReverbRoCC ++ new freechips.rocketchip.system.DefaultConfig)
