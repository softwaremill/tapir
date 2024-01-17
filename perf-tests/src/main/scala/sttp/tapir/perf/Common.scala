package sttp.tapir.perf

import java.io.File
import java.nio.file.Path
import java.util.Date
import scala.util.Random

object Common {
  val rootPackage = "sttp.tapir.perf"
  val LargeInputSize = 5 * 1024 * 1024
  val Port = 8080
  val TmpDir: File = new java.io.File(System.getProperty("java.io.tmpdir")).getAbsoluteFile
  def tempFilePath(): Path = TmpDir.toPath.resolve(s"tapir-${new Date().getTime}-${Random.nextLong()}")

}
