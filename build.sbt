name := "upkoder"

version := "0.1"

scalaVersion := "2.11.6"

libraryDependencies ++= {
  val akkaStreamV = "1.0-M3"
  val sprayV = "1.3.3"
  Seq(
    "io.spray"               %%  "spray-can"     % sprayV,
    "io.spray"               %%  "spray-client"     % sprayV,
    "io.spray"               %%  "spray-routing" % sprayV,
    "com.github.nscala-time" %% "nscala-time" % "1.8.0",
    "com.typesafe.akka"      %% "akka-contrib" % "2.3.9",
    "com.typesafe.akka"      %% "akka-testkit" % "2.3.9",
    "com.typesafe.akka"      %% "akka-stream-experimental"          % akkaStreamV,
    "com.typesafe.akka"      %% "akka-http-core-experimental"       % akkaStreamV,
    "com.typesafe.akka"      %% "akka-http-experimental"            % akkaStreamV,
    "com.typesafe.akka"      %% "akka-http-spray-json-experimental" % akkaStreamV,
    "com.typesafe.akka"      %% "akka-http-testkit-experimental"    % akkaStreamV,
    "com.github.seratch"     %% "awscala" % "0.5.+",
    "org.scalatest"          %% "scalatest" % "2.2.1" % "test",
    "commons-io"             % "commons-io" % "2.4" % "test"
  )
}


fork in run := true
