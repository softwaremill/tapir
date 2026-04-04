package sttp.tapir.codegen.testutils

object VersionCheck {
  def runTest(jsonSerde: String)(test: => Unit): Unit = if (jsonSerde != "zio") test else ()
}
