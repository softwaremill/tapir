package sttp.tapir.perf

import io.gatling.core.Predef._
import io.gatling.core.session.Expression
import io.gatling.core.structure.{ChainBuilder, PopulationBuilder}
import io.gatling.http.Predef._
import org.HdrHistogram.{ConcurrentHistogram, Histogram}
import sttp.tapir.perf.Common._

import scala.concurrent.duration._
import scala.util.Random

object CommonSimulations {
  private val baseUrl = "127.0.0.1:8080"
  val DefaultUserCount = 30
  val DefaultDurationSeconds = 30
  val WarmupDurationSeconds = 10
  private val random = new Random()

  def randomByteArray(size: Int): Array[Byte] = {
    val byteArray = new Array[Byte](size)
    random.nextBytes(byteArray)
    byteArray
  }

  def randomAlphanumByteArray(size: Int): Array[Byte] =
    Random.alphanumeric.take(size).map(_.toByte).toArray

  lazy val constRandomLongBytes = randomByteArray(LargeInputSize)
  lazy val constRandomLongAlphanumBytes = randomAlphanumByteArray(LargeInputSize)

  def getParamOpt(paramName: String): Option[String] = Option(System.getProperty(s"tapir.perf.${paramName}"))

  def userCount = getParamOpt("user-count").map(_.toInt).getOrElse(DefaultUserCount)
  def duration(warmup: Boolean) =
    if (warmup) WarmupDurationSeconds else getParamOpt("duration-seconds").map(_.toInt).getOrElse(DefaultDurationSeconds)
  def namePrefix(warmup: Boolean) = if (warmup) "[WARMUP] " else ""

  val responseTimeKey = "responseTime"
  def sessionSaveResponseTime = responseTimeInMillis.saveAs(responseTimeKey)
  def handleLatencyHistogram(histogram: Histogram, warmup: Boolean): Expression[Session] = { session =>
    if (warmup) {
      histogram.reset()
    } else {
      val responseTime = session("responseTime").as[Int]
      histogram.recordValue(responseTime.toLong)
    }
    session

  }

  val httpProtocol = http.baseUrl(s"http://$baseUrl")
  val wsPubHttpProtocol = http.wsBaseUrl(s"ws://$baseUrl/ws")

  def scenario_simple_get(routeNumber: Int, histogram: Histogram, warmup: Boolean = false): PopulationBuilder = {
    val execHttpGet: ChainBuilder = exec(
      http(s"${namePrefix(warmup)}HTTP GET /path$routeNumber/4")
        .get(s"/path$routeNumber/4")
        .check(sessionSaveResponseTime)
    )
      .exec(handleLatencyHistogram(histogram, warmup))

    scenario(s"${namePrefix(warmup)} Repeatedly invoke GET of route number $routeNumber")
      .during(duration(warmup))(execHttpGet)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)
  }

  def scenario_post_string(routeNumber: Int, histogram: Histogram, warmup: Boolean = false): PopulationBuilder = {
    val execHttpPost = exec(
      http(s"${namePrefix(warmup)}HTTP POST /path$routeNumber")
        .post(s"/path$routeNumber")
        .body(StringBody(_ => new String(randomAlphanumByteArray(256))))
        .header("Content-Type", "text/plain")
        .check(sessionSaveResponseTime)
    )
      .exec(handleLatencyHistogram(histogram, warmup))

    scenario(s"${namePrefix(warmup)} Repeatedly invoke POST with short string body")
      .during(duration(warmup))(execHttpPost)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)

  }
  def scenario_post_bytes(routeNumber: Int, histogram: Histogram, warmup: Boolean = false): PopulationBuilder = {
    val execHttpPost = exec(
      http(s"${namePrefix(warmup)}HTTP POST /pathBytes$routeNumber")
        .post(s"/pathBytes$routeNumber")
        .body(ByteArrayBody(_ => randomAlphanumByteArray(256)))
        .header("Content-Type", "text/plain") // otherwise Play complains
        .check(sessionSaveResponseTime)
    )
      .exec(handleLatencyHistogram(histogram, warmup))

    scenario(s"${namePrefix(warmup)} Repeatedly invoke POST with short byte array body")
      .during(duration(warmup))(execHttpPost)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)
  }

  def scenario_post_long_bytes(routeNumber: Int, histogram: Histogram, warmup: Boolean = false): PopulationBuilder = {
    val execHttpPost = exec(
      http(s"${namePrefix(warmup)}HTTP POST /pathBytes$routeNumber")
        .post(s"/pathBytes$routeNumber")
        .body(ByteArrayBody(constRandomLongAlphanumBytes))
        .header("Content-Type", "text/plain") // otherwise Play complains
        .check(sessionSaveResponseTime)
    )
      .exec(handleLatencyHistogram(histogram, warmup))

    scenario(s"${namePrefix(warmup)} Repeatedly invoke POST with large byte array")
      .during(duration(warmup))(execHttpPost)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)
  }

  def scenario_post_long_string(routeNumber: Int, histogram: Histogram, warmup: Boolean = false): PopulationBuilder = {
    val execHttpPost = exec(
      http(s"${namePrefix(warmup)}HTTP POST /path$routeNumber")
        .post(s"/path$routeNumber")
        .body(ByteArrayBody(constRandomLongAlphanumBytes))
        .header("Content-Type", "text/plain")
        .check(sessionSaveResponseTime)
    )
      .exec(handleLatencyHistogram(histogram, warmup))

    scenario(s"${namePrefix(warmup)} Repeatedly invoke POST with large byte array, interpreted to a String")
      .during(duration(warmup))(execHttpPost)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)
  }

}

import CommonSimulations._

abstract class PerfTestSuiteRunnerSimulation extends Simulation {
  lazy val histogram = new ConcurrentHistogram(1L, 10000L, 3)

  before {
    println("Resetting Histogram")
    histogram.reset
  }

  after {
    HistogramPrinter.saveToFile(histogram, getClass.getSimpleName)
  }
}

class SimpleGetSimulation extends PerfTestSuiteRunnerSimulation {
  val warmup = scenario_simple_get(0, histogram, warmup = true)
  val measurements = scenario_simple_get(0, histogram)
  setUp(warmup.andThen(measurements)): Unit
}

class SimpleGetMultiRouteSimulation extends PerfTestSuiteRunnerSimulation {
  val warmup = scenario_simple_get(127, histogram, warmup = true)
  val measurements = scenario_simple_get(127, histogram)
  setUp(warmup.andThen(measurements)): Unit
}

class PostBytesSimulation extends PerfTestSuiteRunnerSimulation {
  val warmup = scenario_post_bytes(0, histogram, warmup = true)
  val measurements = scenario_post_bytes(0, histogram)
  setUp(warmup.andThen(measurements)): Unit
}

class PostLongBytesSimulation extends PerfTestSuiteRunnerSimulation {
  val warmup = scenario_post_long_bytes(0, histogram, warmup = true)
  val measurements = scenario_post_long_bytes(0, histogram)
  setUp(warmup.andThen(measurements)): Unit
}

class PostStringSimulation extends PerfTestSuiteRunnerSimulation {
  val warmup = scenario_post_string(0, histogram, warmup = true)
  val measurements = scenario_post_string(0, histogram)
  setUp(warmup.andThen(measurements)): Unit
}

class PostLongStringSimulation extends PerfTestSuiteRunnerSimulation {
  val warmup = scenario_post_long_string(0, histogram, warmup = true)
  val measurements = scenario_post_long_string(0, histogram)
  setUp(warmup.andThen(measurements)): Unit
}

/** Based on https://github.com/kamilkloch/websocket-benchmark/ Can't be executed using PerfTestSuiteRunner, see perfTests/README.md
  */
class WebSocketsSimulation extends Simulation {
  val scenarioUserCount = 2500
  val scenarioDuration = 30.seconds
  lazy val histogram = new ConcurrentHistogram(1L, 10000L, 3)

  after {
    HistogramPrinter.saveToFile(histogram, "ws-latency")
  }

  /** Sends requests after connecting a user to a WebSocket. For each request, waits at most 1 second for a response. The response body
    * carries a timestamp which is subtracted from current timestamp to calculate latency. The latency represents time between server
    * finishing preparing a message and client receiving the message, so it's unidirectional. Some artificial lag may be introduced on the
    * server (like 100 milliseconds) producing responses, so that it simulates an emitter producing data as a stream, and our test measures
    * only the latency of returning data once its prepared. This method has to operate in such a mode because Gatling operates in a
    * request->response flow.
    */
  private def wsSubscribe(name: String, histogram: Histogram): ChainBuilder = {
    repeat(WebSocketRequestsPerUser, "i")(
      ws(name)
        .sendText("0")
        .await(1.second)(
          ws
            .checkTextMessage(name)
            .check(bodyString.transform { ts =>
              histogram.recordValue(Math.max(System.currentTimeMillis() - ts.toLong, 0))
            })
        )
    )
  }
  val warmup = scenario("WS warmup")
    .exec(
      ws("Warmup Connect WS").connect("/ts"),
      wsSubscribe("Warmup Subscribe", histogram),
      ws("Warmup Close WS").close,
      exec({ session =>
        histogram.reset()
        session
      })
    )
    .inject(rampUsers(userCount).during(duration(warmup = true)))

  val measurement = scenario("WebSockets measurements")
    .exec(
      ws("Connect WS").connect("/ts"),
      wsSubscribe("Subscribe", histogram),
      ws("Close WS").close
    )
    .inject(rampUsers(userCount).during(duration(warmup = false)))

  setUp(
    warmup.andThen(measurement)
  ).protocols(wsPubHttpProtocol): Unit
}
