package sttp.tapir.codegen.testutils

object VersionCheck {
  def runTest(jsonSerde: String)(test: => Unit): Unit =
    if (jsonSerde == "zio" && Option(System.getProperty("java.version")).exists(_.startsWith("11"))) ()
    else test
}
