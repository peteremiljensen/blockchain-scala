name := """blockchain"""
organization := "dk.diku"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.11"
lazy val akkaVersion = "2.4.11"

// scalaz-bintray resolver needed for specs2 library
resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test
libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test
libraryDependencies ++= Seq("com.roundeights" %% "hasher" % "1.2.0")

// INSPIRED BY: https://github.com/playframework/play-scala-websocket-example
