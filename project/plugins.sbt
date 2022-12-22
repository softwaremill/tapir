val sbtSoftwareMillVersion = "2.0.12"
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-common" % sbtSoftwareMillVersion)
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-publish" % sbtSoftwareMillVersion)
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-browser-test-js" % sbtSoftwareMillVersion)
//addSbtPlugin("io.spray" % "sbt-boilerplate" % "0.6.1")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.5.1")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.3.6")
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.9.0")
addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "1.1.1")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.12.0")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.0.0")
addSbtPlugin("io.gatling" % "gatling-sbt" % "4.2.4")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.9")
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.1.4")

addDependencyTreePlugin
