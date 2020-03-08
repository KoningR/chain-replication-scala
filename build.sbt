name := "chain-replication-scala"

version := "0.1"

scalaVersion := "2.13.1"

lazy val akkaVersion = "2.6.3"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.1.0" % Test,
  "com.typesafe.akka" %% "akka-remote" % "2.6.3",
  "com.typesafe.akka" %% "akka-serialization-jackson" % "2.6.3"
)
