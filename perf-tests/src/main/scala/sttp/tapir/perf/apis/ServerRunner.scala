package sttp.tapir.perf.apis

import cats.effect.{ExitCode, IO, IOApp}

import scala.reflect.runtime.universe

trait ServerRunner {
  def start: IO[ServerRunner.KillSwitch]
}

/** Can be used as a Main object to run a single server using its short name. Running perfTests/runMain
  * [[sttp.tapir.perf.apis.ServerRunner]] will load special javaOptions configured in build.sbt, enabling recording JFR metrics. This is
  * useful when you want to guarantee that the server runs in a different JVM than test runner, so that memory and CPU metrics are recorded
  * only in the scope of the server JVM.
  */
object ServerRunner extends IOApp {
  type KillSwitch = IO[Unit]
  val NoopKillSwitch = IO.pure(IO.unit)
  private val runtimeMirror = universe.runtimeMirror(getClass.getClassLoader)

  def run(args: List[String]): IO[ExitCode] = {
    val shortServerName = args.head
    for {
      killSwitch <- startServerByTypeName(ServerName.fromShort(shortServerName))
      _ <- IO.never.guarantee(killSwitch)
    } yield ExitCode.Success
  }

  def startServerByTypeName(serverName: ServerName): IO[ServerRunner.KillSwitch] = {
    serverName match {
      case ExternalServerName => NoopKillSwitch
      case _ =>
        try {
          val moduleSymbol = runtimeMirror.staticModule(serverName.fullName)
          val moduleMirror = runtimeMirror.reflectModule(moduleSymbol)
          val instance: ServerRunner = moduleMirror.instance.asInstanceOf[ServerRunner]
          instance.start
        } catch {
          case e: Throwable =>
            IO.raiseError(
              new IllegalArgumentException(s"ERROR! Could not find object ${serverName.fullName} or it doesn't extend ServerRunner", e)
            )
        }
    }
  }
}
