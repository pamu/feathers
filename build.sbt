name := "fextensions"

version := "0.0.1"

scalaVersion := "2.12.0"

crossScalaVersions := Seq("2.11.8", "2.12.0")

libraryDependencies ++= Seq("org.typelevel" %% "cats-core" % "1.0.1")

libraryDependencies ++= Seq("org.scalatest" %% "scalatest" % "3.0.0" % "test")

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings")
