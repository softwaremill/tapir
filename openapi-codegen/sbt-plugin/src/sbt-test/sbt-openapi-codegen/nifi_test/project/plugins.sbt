{
  val pluginVersion = Option(System.getProperty("plugin.version")).getOrElse("1.11.50.17-LOCAL")
  if (pluginVersion == null)
    throw new RuntimeException("""|
                                  |
                                  |The system property 'plugin.version' is not defined.
                                  |Specify this property using the scriptedLaunchOpts -D.
                                  |
                                  |""".stripMargin)
  else addSbtPlugin("com.softwaremill.sttp.tapir" % "sbt-openapi-codegen" % pluginVersion)
}
