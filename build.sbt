name := """freechain"""
organization := "dk.diku"

version := "1.0"

scalaVersion := "2.12.2"
lazy val akkaVersion = "2.5.2"

// scalaz-bintray resolver needed for specs2 library
resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test
libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test
libraryDependencies += "com.typesafe.akka" %% "akka-http-core" % "10.0.6"
libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.0.6"
libraryDependencies += "com.roundeights" %% "hasher" % "1.2.0"
libraryDependencies += "org.json4s" %% "json4s-native" % "3.5.2"

scalacOptions += "-deprecation"
scalacOptions += "-feature"


// INSPIRED BY: https://github.com/playframework/play-scala-websocket-example
