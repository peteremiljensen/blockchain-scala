name := """blockchain"""
organization := "dk.diku"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.11"
lazy val akkaVersion = "2.4.11"

// scalaz-bintray resolver needed for specs2 library
resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

libraryDependencies += filters
libraryDependencies += ws
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test
libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % Test
libraryDependencies ++= Seq("com.roundeights" %% "hasher" % "1.2.0")

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "dk.diku.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "dk.diku.binders._"

// INSPIRED BY: https://github.com/playframework/play-scala-websocket-example
