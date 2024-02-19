package sttp.tapir.perf

import io.gatling.core.Predef._
import io.gatling.core.structure.{ChainBuilder, PopulationBuilder}
import io.gatling.http.Predef._
import org.HdrHistogram.{ConcurrentHistogram, Histogram}
import sttp.tapir.perf.Common._

import java.io.{FileOutputStream, PrintStream}
import java.nio.file.Paths
import scala.concurrent.duration._
import scala.util.{Failure, Random, Success, Using}

object CommonSimulations {
  private val baseUrl = "127.0.0.1:8080"
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

  def getParam(paramName: String): String =
    getParamOpt(paramName).getOrElse(
      throw new IllegalArgumentException(
        s"Missing tapir.perf.${paramName} system property, ensure you're running perf tests correctly (see perfTests/README.md)"
      )
    )

  def userCount = getParam("user-count").toInt
  def duration = getParam("duration-seconds").toInt
  def namePrefix = if (getParamOpt("is-warm-up").map(_.toBoolean) == Some(true)) "[WARMUP] " else ""
  val httpProtocol = http.baseUrl(s"http://$baseUrl")
  val wsPubHttpProtocol = http.wsBaseUrl(s"ws://$baseUrl/ws")

  def scenario_simple_get(routeNumber: Int): PopulationBuilder = {
    val execHttpGet: ChainBuilder = exec(http(s"HTTP GET /path$routeNumber/4").get(s"/path$routeNumber/4"))

    scenario(s"${namePrefix}Repeatedly invoke GET of route number $routeNumber")
      .during(duration)(execHttpGet)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)
  }

  def scenario_post_string(routeNumber: Int): PopulationBuilder = {
    val execHttpPost = exec(
      http(s"HTTP POST /path$routeNumber")
        .post(s"/path$routeNumber")
        .body(StringBody(_ => new String(randomAlphanumByteArray(256))))
        .header("Content-Type", "text/plain")
    )

    scenario(s"${namePrefix}Repeatedly invoke POST with short string body")
      .during(duration)(execHttpPost)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)

  }
  def scenario_post_bytes(routeNumber: Int): PopulationBuilder = {
    val execHttpPost = exec(
      http(s"HTTP POST /pathBytes$routeNumber")
        .post(s"/pathBytes$routeNumber")
        .body(ByteArrayBody(_ => randomAlphanumByteArray(256)))
        .header("Content-Type", "text/plain") // otherwise Play complains
    )

    scenario(s"${namePrefix}Repeatedly invoke POST with short byte array body")
      .during(duration)(execHttpPost)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)
  }

  def scenario_post_file(routeNumber: Int): PopulationBuilder = {
    val execHttpPost = exec(
      http(s"HTTP POST /pathFile$routeNumber")
        .post(s"/pathFile$routeNumber")
        .body(ByteArrayBody(constRandomLongBytes))
        .header("Content-Type", "application/octet-stream")
    )

    scenario(s"${namePrefix}Repeatedly invoke POST with file body")
      .during(duration)(execHttpPost)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)
  }

  def scenario_post_long_bytes(routeNumber: Int): PopulationBuilder = {
    val execHttpPost = exec(
      http(s"HTTP POST /pathBytes$routeNumber")
        .post(s"/pathBytes$routeNumber")
        .body(ByteArrayBody(constRandomLongAlphanumBytes))
        .header("Content-Type", "text/plain") // otherwise Play complains
    )

    scenario(s"${namePrefix}Repeatedly invoke POST with large byte array")
      .during(duration)(execHttpPost)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)
  }

  def scenario_post_long_string(routeNumber: Int): PopulationBuilder = {
    val execHttpPost = exec(
      http(s"HTTP POST /path$routeNumber")
        .post(s"/path$routeNumber")
        .body(ByteArrayBody(constRandomLongAlphanumBytes))
        .header("Content-Type", "text/plain")
    )

    scenario(s"${namePrefix}Repeatedly invoke POST with large byte array, interpreted to a String")
      .during(duration)(execHttpPost)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)
  }

}

import CommonSimulations._

abstract class PerfTestSuiteRunnerSimulation extends Simulation

class SimpleGetSimulation extends PerfTestSuiteRunnerSimulation {
  setUp(scenario_simple_get(0)): Unit
}

class SimpleGetMultiRouteSimulation extends PerfTestSuiteRunnerSimulation {
  setUp(scenario_simple_get(127)): Unit
}

class PostBytesSimulation extends PerfTestSuiteRunnerSimulation {
  setUp(scenario_post_bytes(0)): Unit
}

class PostLongBytesSimulation extends PerfTestSuiteRunnerSimulation {
  setUp(scenario_post_long_bytes(0)): Unit
}

class PostFileSimulation extends PerfTestSuiteRunnerSimulation {
  setUp(scenario_post_file(0)): Unit
}

class PostStringSimulation extends PerfTestSuiteRunnerSimulation {
  setUp(scenario_post_string(0)): Unit
}

class PostLongStringSimulation extends PerfTestSuiteRunnerSimulation {
  setUp(scenario_post_long_string(0)): Unit
}

/** Based on https://github.com/kamilkloch/websocket-benchmark/ Can't be executed using PerfTestSuiteRunner, see perfTests/README.md
  */
class WebSocketsSimulation extends Simulation {
  val scenarioUserCount = 2500
  val scenarioDuration = 30.seconds

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

  val histogram = new ConcurrentHistogram(1L, 10000L, 3)
  Runtime.getRuntime.addShutdownHook(new Thread {
    override def run(): Unit = {
      val baseDir = System.getProperty("user.dir")
      val targetFilePath = Paths.get(baseDir).resolve(s"websockets-latency-${System.currentTimeMillis()}")
      Using.Manager { use =>
        val fos = use(new FileOutputStream(targetFilePath.toFile))
        val ps = use(new PrintStream(fos))
        histogram.outputPercentileDistribution(System.out, 1.0)
        histogram.outputPercentileDistribution(ps, 1.0)
      } match {
        case Success(_) =>
          println(s"******* Histogram saved to $targetFilePath")
        case Failure(ex) =>
          ex.printStackTrace
      }
    }
  })
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
    .inject(rampUsers(scenarioUserCount).during(scenarioDuration))

  val measurement = scenario("WebSockets measurements")
    .exec(
      ws("Connect WS").connect("/ts"),
      wsSubscribe("Subscribe", histogram),
      ws("Close WS").close
    )
    .inject(rampUsers(scenarioUserCount).during(scenarioDuration))

  setUp(
    warmup.andThen(measurement)
  ).protocols(wsPubHttpProtocol): Unit
}
