name := "missing-futures-lib"

version := "0.0.1"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq()

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.0" % "test"

libraryDependencies += "com.lihaoyi" % "ammonite" % "0.8.0" % "test" cross CrossVersion.full

initialCommands in (Test, console) := """ammonite.Main().run()"""