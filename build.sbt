name := "ergo-tg"

version := "0.0.1"

organization := "com.github.oskin1"

scalaVersion := "2.12.10"

resolvers ++= Seq(
  "Sonatype Public" at "https://oss.sonatype.org/content/groups/public/",
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Typesafe maven releases" at "http://repo.typesafe.com/typesafe/maven-releases/"
)

val ergoWalletVersion = "key-serialization-9dee6be9-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.ergoplatform" %% "ergo-wallet"      % ergoWalletVersion,
  "org.typelevel"    %% "cats-effect"      % "2.0.0-RC2",
  "dev.zio"          %% "zio"              % "1.0.0-RC13",
  "dev.zio"          %% "zio-interop-cats" % "2.0.0.0-RC3",
  "co.fs2"           %% "fs2-core"         % "2.0.1",
  "org.augustjune"   %% "canoe"            % "0.1.2",

  ("org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8").exclude("org.iq80.leveldb", "leveldb"),
  "org.iq80.leveldb" % "leveldb" % "0.12",

  "org.scalactic" %% "scalactic" % "3.0.5",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
)

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-language:experimental.macros",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  "-unchecked",
  "-Xfuture",
  "-Yno-adapted-args",
  "-Ywarn-numeric-widen",
  "-Ypartial-unification"
)

test in assembly := {}

assemblyJarName in assembly := s"${name.value}-${version.value}.jar"

assemblyMergeStrategy in assembly := {
  case "logback.xml"       => MergeStrategy.first
  case "module-info.class" => MergeStrategy.discard
  case other               => (assemblyMergeStrategy in assembly).value(other)
}
