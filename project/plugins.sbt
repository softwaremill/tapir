// https://github.com/sbt/sbt/issues/6997
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

val sbtSoftwareMillVersion = "2.0.18"
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-common" % sbtSoftwareMillVersion)
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-publish" % sbtSoftwareMillVersion)
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-browser-test-js" % sbtSoftwareMillVersion)
//addSbtPlugin("io.spray" % "sbt-boilerplate" % "0.6.1")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.3")
addSbtPlugin("org.playframework.twirl" % "sbt-twirl" % "2.0.3")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.5.1")
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.9.1")
addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "1.1.2")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.14.0")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.5")
addSbtPlugin("io.gatling" % "gatling-sbt" % "4.7.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.16")
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.1.4")
addSbtPlugin("org.apache.pekko" % "pekko-grpc-sbt-plugin" % "1.0.1")
// needed to override the Android flavor of Guava coming from pekko-grpc-sbt-plugin, which causes failures in Scala.JS builds
dependencyOverrides += "com.google.guava" % "guava" % "32.1.2-jre"

addDependencyTreePlugin
