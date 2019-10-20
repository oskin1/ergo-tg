name := "ergo-tg"

version := "1.1.0"

organization := "com.github.oskin1"

scalaVersion := "2.12.10"

resolvers ++= Seq(
  "Sonatype Public" at "https://oss.sonatype.org/content/groups/public/",
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Typesafe maven releases" at "http://repo.typesafe.com/typesafe/maven-releases/"
)

val ergoWalletVersion = "master-83d10111-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.ergoplatform"      %% "ergo-wallet"      % ergoWalletVersion,
  "org.typelevel"         %% "cats-effect"      % "2.0.0-RC2",
  "dev.zio"               %% "zio"              % "1.0.0-RC13",
  "dev.zio"               %% "zio-interop-cats" % "2.0.0.0-RC3",
  "co.fs2"                %% "fs2-core"         % "2.0.1",
  "org.augustjune"        %% "canoe"            % "0.2.0",
  "com.github.pureconfig" %% "pureconfig"       % "0.12.1",
  "com.lihaoyi"           %% "fastparse"        % "2.1.3",

  ("org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8").exclude("org.iq80.leveldb", "leveldb"),
  "org.iq80.leveldb" % "leveldb" % "0.12",

  "org.scalactic"  %% "scalactic"  % "3.0.8"  % "test",
  "org.scalatest"  %% "scalatest"  % "3.0.8"  % "test",
  "org.scalacheck" %% "scalacheck" % "1.14.1" % "test"
)

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-language:experimental.macros",
  "-language:higherKinds",
  "-language:postfixOps",
  "-language:implicitConversions",
  "-feature",
  "-unchecked",
  "-Xfuture",
  "-Yno-adapted-args",
  "-Ywarn-numeric-widen",
  "-Ypartial-unification"
)

mainClass in assembly := Some("com.github.oskin1.wallet.WalletApp")

test in assembly := {}

assemblyJarName in assembly := s"${name.value}-${version.value}.jar"

assemblyMergeStrategy in assembly := {
  case "logback.xml"                               => MergeStrategy.first
  case "module-info.class"                         => MergeStrategy.discard
  case PathList("org", "iq80", "leveldb", xs @ _*) => MergeStrategy.first
  case other                                       => (assemblyMergeStrategy in assembly).value(other)
}
