package sttp.tapir.codegen.testutils

object VersionCheck {
  def runTest(jsonSerde: String)(test: => Unit): Unit = if (jsonSerde == "circe") test else ()
}