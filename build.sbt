name := "missing-futures-library"

version := "0.0.1"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq()

libraryDependencies += "com.lihaoyi" % "ammonite" % "0.8.0" % "test" cross CrossVersion.full

initialCommands in (Test, console) := """ammonite.Main().run()"""