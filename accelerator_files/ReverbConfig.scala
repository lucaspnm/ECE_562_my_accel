package reverb

import freechips.rocketchip.config.Config
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tile._
import freechips.rocketchip.system.BaseConfig

/** 
  * Adds the ReverbRoCC accelerator to the Rocket Chip configuration.
  * This matches the reverb_driver.c which uses opcode custom0.
  */
class WithReverbRoCC extends Config((site, here, up) => {
  case BuildRoCC => (p: Parameters) => Seq(
    LazyModule(new ReverbRoCC(opcode = 0)(p)) // opcode = custom0
  )
})

/**
  * Defines a full system configuration that includes the ReverbRoCC.
  * Based on the standard Rocket base config.
  */
class ReverbAcceleratorConfig extends Config(
  new WithReverbRoCC ++
  new freechips.rocketchip.system.BaseConfig
)
