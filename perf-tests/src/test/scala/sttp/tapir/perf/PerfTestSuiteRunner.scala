package sttp.tapir.perf

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all._
import fs2.io.file
import fs2.text
import sttp.tapir.perf.Common._
import sttp.tapir.perf.apis.ServerRunner

import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.FiniteDuration

/** Main entry point for running suites of performance tests and generating aggregated reports. A suite represents a set of Gatling
  * simulations executed on a set of servers, with some additional parameters like concurrent user count. One can run a single simulation on
  * a single server, as well as a selection of (servers x simulations). The runner then collects Gatling logs from simulation.log files of
  * individual simulation runs and puts them together into an aggregated report comparing results for all the runs. If no server are provided
  * in the arguments, the suite will only execute simulations, assuming a server has been started separately.
  */
object PerfTestSuiteRunner extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val params = PerfTestSuiteParams.parse(args)
    println("===========================================================================================")
    println(s"Running a suite of ${params.totalTests} tests, each for ${params.users} users and ${params.duration}.")
    println(s"Additional warm-up phase of $WarmupDuration will be performed before each simulation.")
    println(s"Servers: ${params.shortServerNames}")
    println(s"Simulations: ${params.shortSimulationNames}")
    println(s"Expected total duration: at least ${params.minTotalDuration}")
    println("Generated suite report paths will be printed to stdout after all tests are finished.")
    println("===========================================================================================")

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm_ss")
    val currentTime = LocalDateTime.now().format(formatter)
    ((params.simulationNames, params.serverNames)
      .mapN((x, y) => (x, y)))
      .traverse { case ((simulationName, shortSimulationName), serverName) =>
        for {
          serverKillSwitch <- ServerRunner.startServerByTypeName(serverName)
          _ <- IO.println(s"Running server ${serverName.shortName}, simulation $simulationName")
          _ <- (for {
            _ <- IO.println("======================== WARM-UP ===============================================")
            _ = setSimulationParams(users = WarmupUsers, duration = WarmupDuration, warmup = true)
            _ <- IO.blocking(GatlingRunner.runSimulationBlocking(simulationName, params)) // warm-up
            _ <- IO.println("==================== WARM-UP COMPLETED =========================================")
            _ = setSimulationParams(users = params.users, duration = params.duration, warmup = false)
            simResultCode <- IO.blocking(GatlingRunner.runSimulationBlocking(simulationName, params)) // actual test
          } yield simResultCode)
            .guarantee(serverKillSwitch)
            .ensureOr(errCode => new Exception(s"Gatling failed with code $errCode"))(_ == 0)
          serverSimulationResult <- GatlingLogProcessor.processLast(shortSimulationName, serverName)
          _ <- IO.println(serverSimulationResult)
        } yield (serverSimulationResult)
      }
      .flatTap(writeCsvReport(currentTime, params.simulationNames.map(_._2)))
      .flatTap(writeHtmlReport(currentTime))
      .as(ExitCode.Success)
  }

  /** Gatling doesn't allow to pass parameters to simulations when they are run using `Gatling.fromMap()`, that's why we're using system
    * parameters as global variables to customize some params.
    */
  private def setSimulationParams(users: Int, duration: FiniteDuration, warmup: Boolean): Unit = {
    System.setProperty("tapir.perf.user-count", users.toString)
    System.setProperty("tapir.perf.duration-seconds", duration.toSeconds.toString)
    System.setProperty("tapir.perf.is-warm-up", warmup.toString): Unit
  }

  private def writeCsvReport(currentTime: String, initialSimOrdering: List[String])(results: List[GatlingSimulationResult]): IO[Unit] = {
    val csv = CsvReportPrinter.print(results, initialSimOrdering)
    writeReportFile(csv, "csv", currentTime)
  }

  private def writeHtmlReport(currentTime: String)(results: List[GatlingSimulationResult]): IO[Unit] = {
    val html = HtmlReportPrinter.print(results)
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
