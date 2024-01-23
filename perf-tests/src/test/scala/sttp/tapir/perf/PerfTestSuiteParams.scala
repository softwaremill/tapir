package sttp.tapir.perf

import scala.concurrent.duration._
import Common._
import scopt.OParser
import scala.util.Failure
import scala.util.Success

/** Parameters to customize a suite of performance tests. */
case class PerfTestSuiteParams(
    shortServerNames: List[String] = Nil,
    shortSimulationNames: List[String] = Nil,
    users: Int = PerfTestSuiteParams.defaultUserCount,
    durationSeconds: Int = PerfTestSuiteParams.defaultDurationSeconds,
    buildGatlingReports: Boolean = false
) {
  def adjustWildcards: PerfTestSuiteParams = {
    val withAdjustedServer: PerfTestSuiteParams =
      if (shortServerNames == List("*")) copy(shortServerNames = TypeScanner.allServers) else this
    if (shortSimulationNames == List("*"))
      withAdjustedServer.copy(shortSimulationNames = TypeScanner.allSimulations)
    else
      withAdjustedServer
  }

  def duration: FiniteDuration = durationSeconds.seconds

  def totalTests: Int = shortServerNames.length * shortSimulationNames.length

  def minTotalDuration: FiniteDuration = ((duration + WarmupDuration) * totalTests.toLong).toMinutes.minutes

  /** Returns pairs of (fullServerName, shortServerName), for example: (sttp.tapir.perf.pekko.TapirServer, pekko.Tapir)
    */
  def serverNames: List[(String, String)] = shortServerNames.map(s => s"${rootPackage}.${s}Server").zip(shortServerNames).distinct

  /** Returns pairs of (fullSimulationName, shortSimulationName), for example: (sttp.tapir.perf.SimpleGetSimulation, SimpleGet)
    */
  def simulationNames: List[(String, String)] =
    shortSimulationNames.map(s => s"${rootPackage}.${s}Simulation").zip(shortSimulationNames).distinct
}

object PerfTestSuiteParams {
  val defaultUserCount = 1
  val defaultDurationSeconds = 10
  val builder = OParser.builder[PerfTestSuiteParams]
  import builder._
  val argParser = OParser.sequence(
    programName("perf"),
    opt[Seq[String]]('s', "server")
      .required()
      .action((x, c) => c.copy(shortServerNames = x.toList))
      .text(s"Comma-separated list of short server names, or '*' for all. Available servers: ${TypeScanner.allServers.mkString(", ")}"),
    opt[Seq[String]]('m', "sim")
      .required()
      .action((x, c) => c.copy(shortSimulationNames = x.toList))
      .text(s"Comma-separated list of short simulation names, or '*' for all. Available simulations: ${TypeScanner.allSimulations.mkString(", ")}"),
    opt[Int]('u', "users")
      .action((x, c) => c.copy(users = x))
      .text(s"Number of concurrent users, default is $defaultUserCount"),
    opt[Int]('d', "duration")
      .action((x, c) => c.copy(durationSeconds = x))
      .text(s"Single simulation duration in seconds, default is $defaultDurationSeconds"),
    opt[Unit]('g', "gatling-reports")
      .action((_, c) => c.copy(buildGatlingReports = true))
      .text("Generate Gatling reports for individuals sims, may significantly affect total time (disabled by default)")
  )

  def parse(args: List[String]): PerfTestSuiteParams = {
    OParser.parse(argParser, args, PerfTestSuiteParams()) match {
      case Some(p) =>
        val params = p.adjustWildcards
        TypeScanner.enusureExist(params.shortServerNames, params.shortSimulationNames) match {
          case Success(_) => params
          case Failure(ex) =>
            println(ex.getMessage)
            sys.exit(-1)
        }
      case _ =>
        sys.exit(-1)
    }
  }
}
