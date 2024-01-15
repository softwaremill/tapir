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

/** Main entry point for running suites of performance tests and generating aggregated reports. A suite represents a set of Gatling
  * simulations executed on a set of servers, with some additional parameters like concurrent user count. One can run a single simulation on
  * a single server, as well as a selection of (servers x simulations). The runner then collects Gatling logs from simulation.log files of
  * individual simulation runs and puts them together into an aggregated report comparing results for all the runs.
  */
object PerfTestSuiteRunner extends IOApp {

  val runtimeMirror = universe.runtimeMirror(getClass.getClassLoader)

  def run(args: List[String]): IO[ExitCode] = {
    val params = PerfTestSuiteParams.parse(args)
    System.setProperty("tapir.perf.user-count", params.users.toString)
    System.setProperty("tapir.perf.duration-seconds", params.durationSeconds.toString)
    println("===========================================================================================")
    println(s"Running a suite of ${params.totalTests} tests, each for ${params.users} users and ${params.duration}")
    println(s"Servers: ${params.shortServerNames}")
    println(s"Simulations: ${params.shortSimulationNames}")
    println(s"Expected total duration: at least ${params.minTotalDuration}")
    println("Generated suite report paths will be printed to stdout after all tests are finished.")
    println("===========================================================================================")

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm_ss")
    val currentTime = LocalDateTime.now().format(formatter)
    ((params.simulationNames, params.serverNames)
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
}
