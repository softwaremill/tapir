package sttp.tapir.perf

import scopt.OptionParser
import sttp.tapir.perf.apis.{ExternalServerName, ServerName}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

import Common._

/** Parameters to customize a suite of performance tests. */
case class PerfTestSuiteParams(
    shortServerNames: List[String] = Nil,
    shortSimulationNames: List[String] = Nil,
    users: Int = PerfTestSuiteParams.defaultUserCount,
    durationSeconds: Int = PerfTestSuiteParams.defaultDurationSeconds,
    buildGatlingReports: Boolean = false
) {

  /** Handles server names passed as groups like netty.*, pekko.*, etc. by expanding them into lists of actual server names. Similarly,
    * handles '*' as a short simulation name, expanding it to a list of all simulations.
    * @return
    */
  def adjustWildcards: PerfTestSuiteParams = {
    val withAdjustedServer: PerfTestSuiteParams = {
      val expandedShortServerNames = shortServerNames.flatMap { shortServerName =>
        if (shortServerName.contains("*")) {
          TypeScanner.allServers.filter(_.startsWith(shortServerName.stripSuffix("*")))
        } else List(shortServerName)
      }
      copy(shortServerNames = expandedShortServerNames)
    }
    if (shortSimulationNames == List("*"))
      withAdjustedServer.copy(shortSimulationNames = TypeScanner.allSimulations)
    else
      withAdjustedServer
  }

  def duration: FiniteDuration = durationSeconds.seconds

  def totalTests: Int = shortServerNames.length * shortSimulationNames.length

  def minTotalDuration: FiniteDuration = ((duration + WarmupDuration) * totalTests.toLong).toMinutes.minutes

  /** Returns list of server names
    */
  def serverNames: List[ServerName] = if (shortServerNames.nonEmpty) shortServerNames.map(ServerName.fromShort).distinct else List(ExternalServerName)

  /** Returns pairs of (fullSimulationName, shortSimulationName), for example: (sttp.tapir.perf.SimpleGetSimulation, SimpleGet)
    */
  def simulationNames: List[(String, String)] =
    shortSimulationNames.map(s => s"${rootPackage}.${s}Simulation").zip(shortSimulationNames).distinct
}

object PerfTestSuiteParams {
  val defaultUserCount = 1
  val defaultDurationSeconds = 10
  val argParser = new OptionParser[PerfTestSuiteParams]("perf") {
    opt[Seq[String]]('s', "server")
      .action((x, c) => c.copy(shortServerNames = x.toList))
      .text(
        s"Comma-separated list of short server names, or groups like 'netty.*', 'pekko.*'. If empty, only simulations will be run, assuming already running server. Available servers: ${TypeScanner.allServers
            .mkString(", ")}"
      ): Unit

    opt[Seq[String]]('m', "sim")
      .required()
      .action((x, c) => c.copy(shortSimulationNames = x.toList))
      .text(
        s"Comma-separated list of short simulation names, or '*' for all. Available simulations: ${TypeScanner.allSimulations.mkString(", ")}"
      ): Unit

    opt[Int]('u', "users")
      .action((x, c) => c.copy(users = x))
      .text(s"Number of concurrent users, default is $defaultUserCount"): Unit

    opt[Int]('d', "duration")
      .action((x, c) => c.copy(durationSeconds = x))
      .text(s"Single simulation duration in seconds, default is $defaultDurationSeconds"): Unit

    opt[Unit]('g', "gatling-reports")
      .action((_, c) => c.copy(buildGatlingReports = true))
      .text("Generate Gatling reports for individuals sims, may significantly affect total time (disabled by default)"): Unit
  }

  def parse(args: List[String]): PerfTestSuiteParams = {
    argParser.parse(args, PerfTestSuiteParams()) match {
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
