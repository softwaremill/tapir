package sttp.tapir.perf

import io.gatling.core.Predef._
import io.gatling.core.structure.PopulationBuilder
import io.gatling.http.Predef._
import sttp.tapir.perf.Common._

import scala.util.Random
import io.gatling.core.structure.ChainBuilder

object CommonSimulations {
  private val baseUrl = "http://127.0.0.1:8080"
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
  val httpProtocol = http.baseUrl(baseUrl)

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

class SimpleGetSimulation extends Simulation {
  setUp(scenario_simple_get(0)): Unit
}

class SimpleGetMultiRouteSimulation extends Simulation {
  setUp(scenario_simple_get(127)): Unit
}

class PostBytesSimulation extends Simulation {
  setUp(scenario_post_bytes(0)): Unit
}

class PostLongBytesSimulation extends Simulation {
  setUp(scenario_post_long_bytes(0)): Unit
}

class PostFileSimulation extends Simulation {
  setUp(scenario_post_file(0)): Unit
}

class PostStringSimulation extends Simulation {
  setUp(scenario_post_string(0)): Unit
}

class PostLongStringSimulation extends Simulation {
  setUp(scenario_post_long_string(0)): Unit
}
