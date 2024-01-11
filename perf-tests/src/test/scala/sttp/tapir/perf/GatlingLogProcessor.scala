package sttp.tapir.perf

import cats.effect.IO
import cats.syntax.all._
import com.codahale.metrics.{Histogram, MetricRegistry}
import fs2.io.file.{Files => Fs2Files}
import fs2.text

import java.nio.file.{Files, Path, Paths}
import java.util.stream.Collectors
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Try}

/** Reads all entries from Gatling simulation.log file and calculates mean throughput as well as p99, p95, p75 and p50 latencies.
  */
object GatlingLogProcessor {

  val LogFileName = "simulation.log"

  /**
    * Searches for the last modified simulation.log in all simulation logs and calculates results. 
    */
  def processLast(simulationName: String, serverName: String): IO[GatlingSimulationResult] = {
    for {
      lastLogPath <- IO.fromTry(findLastLogFile)
      _ <- IO.println(s"Processing results from $lastLogPath")
      result <- Fs2Files[IO]
        .readAll(fs2.io.file.Path.fromNioPath(lastLogPath))
        .through(text.utf8.decode)
        .through(text.lines)
        .fold[State](State.initial) { (state, line) =>
          val parts = line.split("\\s+")
          if (parts.length >= 5 && parts(0) == "REQUEST") {
            val requestStartTime = parts(4).toLong
            val minRequestTs = state.minRequestTs.min(requestStartTime)
            val requestEndTime = parts(5).toLong
            val maxResponseTs = state.maxResponseTs.max(requestEndTime)
            val reqDuration = requestEndTime - requestStartTime
            state.histogram.update(reqDuration)
            State(state.histogram, minRequestTs, maxResponseTs)
          } else state
        }
        .compile
        .lastOrError
        .ensure(new IllegalStateException(s"Could not read results from $lastLogPath"))(_.totalDurationMs != State.initial.totalDurationMs)
        .map { state =>
          val snapshot = state.histogram.getSnapshot
          val throughput = (state.histogram.getCount().toDouble / state.totalDurationMs) * 1000
          GatlingSimulationResult(
            simulationName,
            serverName,
            state.totalDurationMs.millis,
            meanReqsPerSec = throughput.toLong,
            latencyP99 = snapshot.get99thPercentile,
            latencyP95 = snapshot.get95thPercentile,
            latencyP75 = snapshot.get75thPercentile,
            latencyP50 = snapshot.getMedian
          )
        }
    } yield result
  }

  private def findLastLogFile: Try[Path] = {
    val baseDir = System.getProperty("user.dir")
    println(s"Base dir = $baseDir")
    val resultsDir: Path = Paths.get(baseDir).resolve("results")
    Try {
      findAllSimulationLogs(resultsDir).maxBy(p => Files.getLastModifiedTime(p))
    }.recoverWith { case err =>
      Failure(new IllegalStateException(s"Could not resolve last ${LogFileName} in ${resultsDir}", err))
    }
  }

  private def findAllSimulationLogs(basePath: Path): List[Path] = {
    Try {
      Files
        .walk(basePath)
        .filter(path => Files.isRegularFile(path) && path.getFileName.toString == LogFileName)
        .collect(Collectors.toList[Path])
        .asScala
        .toList
    }.getOrElse(List.empty[Path])
  }

  case class State(histogram: Histogram, minRequestTs: Long, maxResponseTs: Long) {
    def totalDurationMs: Long = maxResponseTs - minRequestTs
  }

  object State {
    def initial: State = State(new MetricRegistry().histogram("tapir"), Long.MaxValue, 1L)
  }
}
