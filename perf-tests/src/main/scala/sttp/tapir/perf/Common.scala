package sttp.tapir.perf

import java.io.File
import java.nio.file.Path
import java.util.Date

import scala.concurrent.duration._
import scala.util.Random
import sttp.tapir.server.interceptor.CustomiseInterceptors

object Common {
  val rootPackage = "sttp.tapir.perf"
  val LargeInputSize = 5 * 1024 * 1024
  val WarmupDuration = 5.seconds
  val Port = 8080
  val TmpDir: File = new java.io.File(System.getProperty("java.io.tmpdir")).getAbsoluteFile
  def newTempFilePath(): Path = TmpDir.toPath.resolve(s"tapir-${new Date().getTime}-${Random.nextLong()}")
  def buildOptions[F[_], O](customiseInterceptors: CustomiseInterceptors[F, O],  withServerLog: Boolean): O = 
    (if (withServerLog == false)
      customiseInterceptors.serverLog(None)
    else
      customiseInterceptors).options
}
