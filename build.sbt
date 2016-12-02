name := "fextensions"

version := "0.0.1"

scalaVersion := "2.12.0"

crossScalaVersions := Seq("2.11.8", "2.12.0")

libraryDependencies ++= Seq("com.typesafe.akka" %% "akka-actor" % "2.4.13")

libraryDependencies ++= Seq("org.scalatest" %% "scalatest" % "3.0.0" % "test")

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings")