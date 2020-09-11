val sbtSoftwareMillVersion = "1.9.11"
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-common" % sbtSoftwareMillVersion)
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-publish" % sbtSoftwareMillVersion)
addSbtPlugin("io.spray" % "sbt-boilerplate" % "0.6.1")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.8.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.5.0")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.2.8")
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.6.0")
addSbtPlugin("org.jetbrains" % "sbt-ide-settings" % "1.0.0")
