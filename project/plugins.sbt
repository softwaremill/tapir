// https://github.com/sbt/sbt/issues/6997
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

val sbtSoftwareMillVersion = "2.0.21"
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-common" % sbtSoftwareMillVersion)
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-publish" % sbtSoftwareMillVersion)
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-browser-test-js" % sbtSoftwareMillVersion)
//addSbtPlugin("io.spray" % "sbt-boilerplate" % "0.6.1")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.4")
addSbtPlugin("org.playframework.twirl" % "sbt-twirl" % "2.0.7")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.6.1")
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.10.0")
addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "1.1.2")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.17.0")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.0")
addSbtPlugin("io.gatling" % "gatling-sbt" % "4.10.2")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.5")
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.1.4")
addSbtPlugin("org.apache.pekko" % "pekko-grpc-sbt-plugin" % "1.1.0")
// needed to override the Android flavor of Guava coming from pekko-grpc-sbt-plugin, which causes failures in Scala.JS builds
dependencyOverrides += "com.google.guava" % "guava" % "33.3.1-jre"

addDependencyTreePlugin
