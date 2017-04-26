name := """blockchain"""
organization := "dk.diku"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.11"
lazy val akkaVersion = "2.5.0"

// scalaz-bintray resolver needed for specs2 library
resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test
libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test
libraryDependencies += "com.roundeights" %% "hasher" % "1.2.0"
libraryDependencies += "io.spray" %%  "spray-json" % "1.3.3"

// INSPIRED BY: https://github.com/playframework/play-scala-websocket-example
