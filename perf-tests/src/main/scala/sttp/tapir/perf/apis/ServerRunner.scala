package sttp.tapir.perf.apis

import cats.effect.{IO, Resource, ResourceApp}

import scala.reflect.runtime.universe

trait ServerRunner {
  def runServer: Resource[IO, Unit]
}

/** Can be used as a Main object to run a single server using its short name. Running perfTests/runMain
  * [[sttp.tapir.perf.apis.ServerRunner]] will load special javaOptions configured in build.sbt, enabling recording JFR metrics. This is
  * useful when you want to guarantee that the server runs in a different JVM than test runner, so that memory and CPU metrics are recorded
  * only in the scope of the server JVM.
  */
object ServerRunner extends ResourceApp.Forever {

  private val runtimeMirror = universe.runtimeMirror(getClass.getClassLoader)
  private val requireArg: Resource[IO, Unit] = Resource.raiseError(
    new IllegalArgumentException(s"Unspecified server name. Use one of: ${TypeScanner.allServers}"): Throwable
  )
  private def notInstantiated(name: ServerName)(e: Throwable): IO[ServerRunner] = IO.raiseError(
    new IllegalArgumentException(
      s"ERROR! Could not find object ${name.fullName} or it doesn't extend ServerRunner", e
    )
  )

  def run(args: List[String]): Resource[IO, Unit] =
    args.headOption.map(ServerName.fromShort).map(startServerByTypeName).getOrElse(requireArg)

  def startServerByTypeName(serverName: ServerName): Resource[IO, Unit] =
    serverName match {
      case ExternalServerName => Resource.unit
      case _ => Resource.eval(
        IO({
          val moduleSymbol = runtimeMirror.staticModule(serverName.fullName)
          val moduleMirror = runtimeMirror.reflectModule(moduleSymbol)
          moduleMirror.instance.asInstanceOf[ServerRunner]
        }).handleErrorWith(notInstantiated(serverName))
      ).flatMap(_.runServer)
    }
}
