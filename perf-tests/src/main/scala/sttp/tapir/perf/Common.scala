package sttp.tapir.perf

object Common {
  val rootPackage = "sttp.tapir.perf"
  val LargeInputSize = 5 * 1024 * 1024
  val Port = 8080
  val TmpDir = new java.io.File(System.getProperty("java.io.tmpdir")).getAbsoluteFile
}
