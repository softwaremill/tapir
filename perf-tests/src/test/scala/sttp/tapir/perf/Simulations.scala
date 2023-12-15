package sttp.tapir.perf

import io.gatling.core.Predef._
import io.gatling.core.structure.PopulationBuilder
import io.gatling.http.Predef._

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.Random

object CommonSimulations {
  private val userCount = 100
  private val baseUrl = "http://127.0.0.1:8080"

  def testScenario(duration: FiniteDuration, routeNumber: Int): PopulationBuilder = {
    val httpProtocol = http.baseUrl(baseUrl)
    val execHttpGet = exec(http(s"HTTP GET /path$routeNumber/4").get(s"/path$routeNumber/4"))

    scenario(s"Repeatedly invoke GET of route number $routeNumber")
      .during(duration.toSeconds.toInt)(execHttpGet)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)
  }

  def scenario_post_string(duration: FiniteDuration, routeNumber: Int): PopulationBuilder = {
    val httpProtocol = http.baseUrl(baseUrl)
    val execHttpPost = exec(
      http(s"HTTP POST /path$routeNumber/4")
        .post(s"/path$routeNumber/4")
        .body(StringBody(List.fill(256)('x').mkString))
    )

    scenario(s"Repeatedly invoke POST with string body of route number $routeNumber")
      .during(duration.toSeconds.toInt)(execHttpPost)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)
  }

  val random = new Random()

  def randomByteArray(size: Int): Array[Byte] = {
    val byteArray = new Array[Byte](size)
    random.nextBytes(byteArray)
    byteArray
  }
  val constRandomBytes = randomByteArray(262200)

  def scenario_post_bytes_256k(duration: FiniteDuration, routeNumber: Int): PopulationBuilder = {
    val httpProtocol = http.baseUrl(baseUrl)
    val execHttpPost = exec(
      http(s"HTTP POST /pathFile$routeNumber/4")
        .post(s"/pathFile$routeNumber/4")
        .body(ByteArrayBody(constRandomBytes))
        .header("Content-Type", "application/octet-stream")
    )

    scenario(s"Repeatedly invoke POST with file body of route number $routeNumber")
      .during(duration.toSeconds.toInt)(execHttpPost)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)
  }
}

class OneRouteSimulation extends Simulation {
  setUp(CommonSimulations.testScenario(1.minute, 0))
}

class MultiRouteSimulation extends Simulation {
  setUp(CommonSimulations.testScenario(1.minute, 127))
}

class PostStringSimulation extends Simulation {
  setUp(CommonSimulations.scenario_post_string(10.seconds, 127))
}

class PostBytes256Simulation extends Simulation {
  setUp(CommonSimulations.scenario_post_bytes_256k(30.seconds, 122))
}
