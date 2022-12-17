ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.12.8"

lazy val root = (project in file("."))
  .settings(
    name := "StarStation"

      //libraryDependencies +="com.typesafe.akka" %% "akka-http" % "10.2.10"

)
// https://mvnrepository.com/artifact/com.typesafe.akka/akka-http
val AkkaVersion = "2.6.20"
val AkkaHttpVersion = "10.2.10"
libraryDependencies ++=
  Seq(
    "org.scalatest" %% "scalatest" % "3.2.14" % Test,
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion ,
    "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion

  )



