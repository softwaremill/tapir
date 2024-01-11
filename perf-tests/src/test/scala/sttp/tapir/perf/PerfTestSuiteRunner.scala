package sttp.tapir.perf

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all._
import fs2.io.file
import fs2.text
import sttp.tapir.perf.apis.ServerRunner

import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.reflect.runtime.universe
import scala.util.control.NonFatal

/** Main entry point for running suites of performance tests and generating aggregated reports. A suite represents a set of Gatling
  * simulations executed on a set of servers, with some additional parameters like concurrent user count. One can run a single simulation on
  * a single server, as well as a selection of (servers x simulations). The runner then collects Gatling logs from simulation.log files of
  * individual simulation runs and puts them together into an aggregated report comparing results for all the runs.
  */
object PerfTestSuiteRunner extends IOApp {

  val arraySplitPattern = "\\s*[,]+\\s*".r
  val runtimeMirror = universe.runtimeMirror(getClass.getClassLoader)

  def run(args: List[String]): IO[ExitCode] = {
    if (args.length < 2)
      exitOnIncorrectArgs
    val shortServerNames = argToList(args(0))
    val shortSimulationNames = argToList(args(1))
    if (shortSimulationNames.isEmpty || shortServerNames.isEmpty)
      exitOnIncorrectArgs

    val serverNames = shortServerNames.map(s => s"sttp.tapir.perf.${s}Server")
    val simulationNames = shortSimulationNames.map(s => s"sttp.tapir.perf.${s}Simulation")

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm_ss")
    val currentTime = LocalDateTime.now().format(formatter)
    // TODO ensure servers and simulations exist
    // TODO Parse user count
    // TODO add comprehensive help
    ((simulationNames.zip(shortSimulationNames), (serverNames.zip(shortServerNames)))
      .mapN((x, y) => (x, y)))
      .traverse { case ((simulationName, shortSimulationName), (serverName, shortServerName)) =>
        for {
          serverKillSwitch <- startServerByTypeName(serverName)
          _ <- IO
            .blocking(GatlingRunner.runSimulationBlocking(simulationName))
            .guarantee(serverKillSwitch)
            .ensureOr(errCode => new Exception(s"Gatling failed with code $errCode"))(_ == 0)
          serverSimulationResult <- GatlingLogProcessor.processLast(shortSimulationName, shortServerName)
          _ <- IO.println(serverSimulationResult)
        } yield (serverSimulationResult)
      }
      .flatTap(writeCsvReport(currentTime))
      .flatTap(writeHtmlReport(currentTime))
      .as(ExitCode.Success)
  }

  private def argToList(arg: String): List[String] =
    try {
      if (arg.startsWith("[") && arg.endsWith("]"))
        arraySplitPattern.split(arg.drop(1).dropRight(1)).toList
      else
        List(arg)
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
        exitOnIncorrectArgs
    }

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

  private def writeCsvReport(currentTime: String)(results: List[GatlingSimulationResult]): IO[Unit] = {
    val csv = CsvResultsPrinter.print(results)
    writeReportFile(csv, "csv", currentTime)
  }

  private def writeHtmlReport(currentTime: String)(results: List[GatlingSimulationResult]): IO[Unit] = {
    val html = HtmlResultsPrinter.print(results)
    writeReportFile(html, "html", currentTime)
  }

  private def writeReportFile(report: String, extension: String, currentTime: String): IO[Unit] = {
    val baseDir = System.getProperty("user.dir")
    val targetFilePath = Paths.get(baseDir).resolve(s"tapir-perf-tests-${currentTime}.$extension")
    fs2.Stream
      .emit(report)
      .through(text.utf8.encode)
      .through(file.Files[IO].writeAll(fs2.io.file.Path.fromNioPath(targetFilePath)))
      .compile
      .drain >> IO.println(s"******* Test Suite report saved to $targetFilePath")
  }

  private def exitOnIncorrectArgs = {
    println("Usage: perfTests/Test/runMain sttp.tapir.perf.PerfTestSuiteRunner serverName simulationName userCount")
    println("Where serverName and simulationName can be a single name, an array of comma separated names, or '*' for all")
    println("The userCount parameter is optional (default value is 1)")
    sys.exit(1)
  }
}
