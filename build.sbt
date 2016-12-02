name := "fextensions"

version := "0.0.1-v1-SNAPSHOT"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq("com.typesafe.akka" % "akka-actor_2.11" % "2.4.13")

libraryDependencies ++= Seq("org.scalatest" %% "scalatest" % "3.0.0" % "test")

libraryDependencies += "com.lihaoyi" % "ammonite" % "0.8.0" % "test" cross CrossVersion.full

initialCommands in (Test, console) := """ammonite.Main().run()"""
