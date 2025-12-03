package chipyard

import freechips.rocketchip.tile._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import org.chipsalliance.cde.config.{Config, Parameters}
import reverbrocc._


class WithCustomAccelerator extends Config((site, here, up) => {
  case BuildRoCC => Seq((p: Parameters) => LazyModule(
    new CustomAccelerator(OpcodeSet.custom0 | OpcodeSet.custom1)(p)))
})
