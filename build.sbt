enablePlugins(ReactificPlugin)
enablePlugins(UniversalPlugin)
enablePlugins(JavaAppPackaging)

name := "MMwAS"
titleForDocs := "Making Music with Akka Streams"
codePackage := "com.reactific.mmwas"

scalaVersion := "2.12.6"

mainClass := Some("MakingMusicWithAkkaStreams")

val akka_version = "2.5.16"
val prometheus_version = "0.5.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % akka_version,
  "com.typesafe.akka" %% "akka-actor-typed" % akka_version,
  "com.typesafe.akka" %% "akka-http" % "10.1.4",
  
  "com.github.scopt" %% "scopt" % "3.7.0",
  "org.scalactic" %% "scalactic" % "3.0.5",

  "com.pi4j" % "pi4j-core" % "1.1",
  "io.prometheus" % "simpleclient" % prometheus_version,
  "io.prometheus" % "simpleclient_hotspot" % prometheus_version,
  "io.prometheus" % "simpleclient_pushgateway" % prometheus_version,
  "ie.corballis" % "sox-java" % "1.0.3",
  
  "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  "com.typesafe.akka" %% "akka-testkit" % akka_version % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % akka_version % Test

)

javaOptions in Universal ++= Seq(
  // For making Pi4J use dynamic loading of wiring.so
  "-Dpi4j.linking=dynamic",
  
  "--illegal-access=warn",
  
  // For making Pi4J work in debug mode
  "-Dpi4j.debug",

  // -J params will be added as jvm parameters
  "-J-Xmx512m",
  "-J-Xms256m",
  
  // you can access any build setting/task here
  s"-Dversion=${version.value}"
)

parallelExecution in Test := false
