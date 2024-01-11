package sttp.tapir.perf

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all._
import sttp.tapir.perf.apis.ServerRunner

import scala.reflect.runtime.universe

/** Main entry point for running suites of performance tests and generating aggregated reports. A suite represents a set of Gatling
  * simulations executed on a set of servers, with some additional parameters like concurrent user count. One can run a single simulation on
  * a single server, as well as a selection of (servers x simulations). The runner then collects Gatling logs from simulation.log files of
  * individual simulation runs and puts them together into an aggregated report comparing results for all the runs.
  */
object PerfTestSuiteRunner extends IOApp {

  val arraySplitPattern = "\\s*[,]+\\s*".r
  val runtimeMirror = universe.runtimeMirror(getClass.getClassLoader)

  def run(args: List[String]): IO[ExitCode] = {
    if (args.length < 2) {
      println("Usage: perfTests/Test/runMain sttp.tapir.perf.PerfTestSuiteRunner serverName simulationName userCount")
      println("Where serverName and simulationName can be a single name, an array of comma separated names, or '*' for all")
      println("The userCount parameter is optional (default value is 1)")
      sys.exit(1)
    }
    val shortServerNames = argToList(args(0))
    val shortSimulationNames = argToList(args(1))

    val serverNames = shortServerNames.map(s => s"sttp.tapir.perf.${s}Server")
    val simulationNames = shortSimulationNames.map(s => s"sttp.tapir.perf.${s}Simulation")

    println(serverNames)
    println(simulationNames)
    // TODO Parse user count
    // TODO add comprehensive help
    val flatResults: IO[List[GatlingSimulationResult]] =
      ((simulationNames, serverNames).mapN((x, y) => (x, y))).traverse { case (simulationName, serverName) =>
        for {
          serverKillSwitch <- startServerByTypeName(serverName)
          _ <- IO
            .blocking(GatlingRunner.runSimulationBlocking(simulationName))
            .guarantee(serverKillSwitch)
            .ensureOr(errCode => new Exception(s"Gatling failed with code $errCode"))(_ == 0)
          serverSimulationResult <- GatlingLogProcessor.processLast(simulationName, serverName)
          _ <- IO.println(serverSimulationResult)
        } yield (serverSimulationResult)
      }

    flatResults.as(ExitCode.Success)
  }

  private def argToList(arg: String): List[String] =
    if (arg.startsWith("[") && arg.endsWith("]"))
      arraySplitPattern.split(arg.drop(1).dropRight(1)).toList
    else
      List(arg)

  private def startServerByTypeName(serverName: String): IO[ServerRunner.KillSwitch] = {
    try {
      val moduleSymbol = runtimeMirror.staticModule(serverName)
      val moduleMirror = runtimeMirror.reflectModule(moduleSymbol)
      val instance: ServerRunner = moduleMirror.instance.asInstanceOf[ServerRunner]
      instance.start
    } catch {
      case e: Throwable =>
        IO.raiseError(new IllegalArgumentException(s"ERROR! Could not find object $serverName or it doesn't extend ServerRunner", e))
    }
  }

}
