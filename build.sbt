name := """blockchain"""
organization := "dk.diku"

version := "1.0"

scalaVersion := "2.11.11"
lazy val akkaVersion = "2.5.0"

// scalaz-bintray resolver needed for specs2 library
resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test
libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test
libraryDependencies += "com.typesafe.akka" %% "akka-http-core" % "10.0.5"
libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.0.5"
libraryDependencies += "com.roundeights" %% "hasher" % "1.2.0"
libraryDependencies += "io.spray" %%  "spray-json" % "1.3.3"

scalacOptions += "-deprecation"
scalacOptions += "-feature"


// INSPIRED BY: https://github.com/playframework/play-scala-websocket-example
