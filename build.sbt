lazy val ECE562myaccel = (project in file("."))
  .dependsOn(chipyard)
  .settings(
    name := "ECE_562_my_accel",
    scalaVersion := "2.12.19",
    scalacOptions ++= Seq("-deprecation", "-unchecked")
  )
