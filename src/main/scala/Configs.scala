class RocketWithReverbRoCCConfig extends Config(
  new WithReverbRoCC ++ new freechips.rocketchip.system.DefaultConfig)
