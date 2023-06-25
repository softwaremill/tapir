package sttp.tapir.serverless.aws.lambda.runtime

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import scala.concurrent.Promise

private[runtime] object PlatformCompat {
  // No unsafeRunSync in Native version of IOPlatform
  implicit class IOPlatformOps[T](io: IO[T]) {
    def unsafeRunSync()(implicit runtime: IORuntime): T = {
      val result = Promise[T]()
      io.unsafeRunAsync(v => result.complete(v.toTry))
      while (!result.isCompleted) scala.scalanative.runtime.loop()
      result.future.value.get.get
    }
  }

  trait StrictLogging {
    val logger = new ConsoleLogger({
      val parts = getClass.getName().split('.')
      val shortPackageName = parts.init.map(_.take(1)).mkString(".")
      s"$shortPackageName.${parts.last}"
    })
    class ConsoleLogger(loggerName: String) {
      private sealed abstract class Severity(val name: String)
      private object Error extends Severity("ERROR")
      private object Warn extends Severity("WARN")
      private object Info extends Severity("INFO")
      private object Debug extends Severity("DEBUG")
      private object Trace extends Severity("TRACE")

      private def getTimestamp(): String = {
        import scala.scalanative.meta.LinktimeInfo.isWindows
        import scala.scalanative.posix.time._
        import scala.scalanative.posix.timeOps._
        import scala.scalanative.unsafe._
        import scala.scalanative.unsigned._

        if (isWindows) ""
        else {
          val currentTime = stackalloc[timespec]()
          val timeInfo = stackalloc[tm]()

          clock_gettime(CLOCK_REALTIME, currentTime)
          localtime_r(currentTime.at1, timeInfo)

          val length = 25.toUInt
          val timestamp = stackalloc[CChar](length)
          strftime(timestamp, length, c"%Y-%m-%d %H:%M:%S", timeInfo)
          val milliseconds = currentTime.tv_nsec / 1000000
          f"${fromCString(timestamp)},$milliseconds%03d"
        }
      }

      private def log(severity: Severity, msg: String): Unit = {
        val timestamp = getTimestamp()
        val thread = Thread.currentThread().getName()
        println(s"$timestamp [$thread] ${severity.name} ${loggerName} - $msg")
      }
      private def log(severity: Severity, msg: String, cause: Throwable): Unit = log(severity, s"$msg coused by ${cause.getMessage()}")
      private def log(severity: Severity, msg: String, args: Any*): Unit =
        log(severity, args.map(_.toString()).foldLeft(msg)(_.replaceFirst(raw"\{\}", _)))

      // Error
      def error(message: String): Unit = log(Error, message)
      def error(message: String, cause: Throwable): Unit = log(Error, message, cause)
      def error(message: String, args: Any*): Unit = log(Error, message, args)
      def whenErrorEnabled(body: => Unit): Unit = body

      // Warn
      def warn(message: String): Unit = log(Warn, message)
      def warn(message: String, cause: Throwable): Unit = log(Warn, message, cause)
      def warn(message: String, args: Any*): Unit = log(Warn, message, args)
      def whenWarnEnabled(body: => Unit): Unit = body

      // Info
      def info(message: String): Unit = log(Info, message)
      def info(message: String, cause: Throwable): Unit = log(Info, message, cause)
      def info(message: String, args: Any*): Unit = log(Info, message, args)
      def whenInfoEnabled(body: => Unit): Unit = body

      // Debug
      def debug(message: String): Unit = log(Debug, message)
      def debug(message: String, cause: Throwable): Unit = log(Debug, message, cause)
      def debug(message: String, args: Any*): Unit = log(Debug, message, args)
      def whenDebugEnabled(body: => Unit): Unit = body

      // Trace
      def trace(message: String): Unit = log(Trace, message)
      def trace(message: String, cause: Throwable): Unit = log(Trace, message, cause)
      def trace(message: String, args: Any*): Unit = log(Trace, message, args)
      def whenTraceEnabled(body: => Unit): Unit = body
    }

  }
}
