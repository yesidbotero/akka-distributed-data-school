name := "akka-distributed-data-school"

version := "1.0"

scalaVersion := "2.12.3"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.6",
  "com.typesafe.akka" %% "akka-cluster" % "2.5.6",
  "com.typesafe.akka" %% "akka-distributed-data" % "2.5.6"
)
        