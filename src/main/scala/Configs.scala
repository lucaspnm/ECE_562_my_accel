// File: src/main/scala/Configs.scala
package chipyard

import freechips.rocketchip.config._
import freechips.rocketchip.tile._         // OpcodeSet
import freechips.rocketchip.subsystem._    // BuildRoCC
import freechips.rocketchip.diplomacy._    // LazyModule
import reverbrocc._                        // ReverbRoCC

/** Adds the ReverbRoCC to the Rocket tile(s). */
class WithReverbRoCC extends Config((site, here, up) => {
  case BuildRoCC => up(BuildRoCC, site) ++ Seq({ p: Parameters =>
    LazyModule(new ReverbRoCC(OpcodeSet.custom0)(p))
  })
})

/** Final SoC configuration you pass to `make CONFIG=...` */
class RocketWithReverbRoCCConfig extends Config(
  new WithReverbRoCC ++ new RocketConfig
)
