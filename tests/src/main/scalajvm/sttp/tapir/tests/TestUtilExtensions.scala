package sttp.tapir.tests

import java.io.{File, PrintWriter}

import scala.concurrent.Future
import scala.io.Source

trait TestUtilExtensions {
  def writeToFile(s: String): File = writeToFile("test", "tapir", s)

  def writeToFile(fileName: String, suffix: String, s: String): File = {
    val f = File.createTempFile(fileName, suffix)
    new PrintWriter(f) { write(s); close() }
    f.deleteOnExit()
    f
  }

  def readFromFile(f: File): Future[String] = {
    val s = Source.fromFile(f)
    try {
      Future.successful(s.mkString)
    } finally {
      s.close()
    }
  }
}
