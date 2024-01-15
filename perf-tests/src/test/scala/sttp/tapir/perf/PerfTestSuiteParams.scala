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
    users: Int = 1,
    durationSeconds: Int = 10,
    skipGatlingReports: Boolean = false
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

  def minTotalDuration: FiniteDuration = (duration * totalTests.toLong).toMinutes.minutes

  /** Returns pairs of (fullServerName, shortServerName), for example: (sttp.tapir.perf.pekko.TapirServer, pekko.Tapir)
    */
  def serverNames: List[(String, String)] = shortServerNames.map(s => s"${rootPackage}.${s}Server").zip(shortServerNames)

  /** Returns pairs of (fullSimulationName, shortSimulationName), for example: (sttp.tapir.perf.SimpleGetSimulation, SimpleGet)
    */
  def simulationNames: List[(String, String)] = shortSimulationNames.map(s => s"${rootPackage}.${s}Simulation").zip(shortSimulationNames)
}

object PerfTestSuiteParams {
  val builder = OParser.builder[PerfTestSuiteParams]
  import builder._
  val argParser = OParser.sequence(
    programName("perf"),
    opt[Seq[String]]('s', "server")
      .required()
      .action((x, c) => c.copy(shortServerNames = x.toList))
      .text("Comma-separated list of short server names, or '*' for all"),
    opt[Seq[String]]('m', "sim")
      .required()
      .action((x, c) => c.copy(shortSimulationNames = x.toList))
      .text("Comma-separated list of short simulation names, or '*' for all"),
    opt[Int]('u', "users")
      .action((x, c) => c.copy(users = x))
      .text("Number of concurrent users"),
    opt[Int]('d', "duration")
      .action((x, c) => c.copy(durationSeconds = x))
      .text("Single simulation duration in seconds"),
    opt[Boolean]('g', "skip-gatling-reports")
      .action((x, c) => c.copy(skipGatlingReports = x))
      .text("Generate only aggregated suite report, may significantly shorten total time")
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
