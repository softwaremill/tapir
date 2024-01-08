package sttp.tapir.perf

import io.gatling.core.Predef._
import io.gatling.core.structure.PopulationBuilder
import io.gatling.http.Predef._

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.reflect.runtime.universe
import scala.util.Random
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import sttp.tapir.perf.apis.ServerRunner

object CommonSimulations {
  private val largeInputSize = 5 * 1024 * 1024 
  private val baseUrl = "http://127.0.0.1:8080"
  private val random = new Random()

  def randomByteArray(size: Int): Array[Byte] = {
    val byteArray = new Array[Byte](size)
    random.nextBytes(byteArray)
    byteArray
  }

  def randomAlphanumByteArray(size: Int): Array[Byte] =
    Random.alphanumeric.take(size).map(_.toByte).toArray

  lazy val constRandomBytes = randomByteArray(largeInputSize)
  lazy val constRandomAlphanumBytes = randomAlphanumByteArray(largeInputSize)

  def simple_get(duration: FiniteDuration, routeNumber: Int): PopulationBuilder = {
    val httpProtocol = http.baseUrl(baseUrl)
    val execHttpGet = exec(http(s"HTTP GET /path$routeNumber/4").get(s"/path$routeNumber/4"))

    scenario(s"Repeatedly invoke GET of route number $routeNumber")
      .during(duration.toSeconds.toInt)(execHttpGet)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)
  }
  
  def getParamOpt(paramName: String): Option[String] = Option(System.getProperty(s"tapir.perf.${paramName}"))

  def getParam(paramName: String): String = 
    getParamOpt(paramName).getOrElse(
      throw new IllegalArgumentException(s"Missing tapir.perf.${paramName} system property, ensure you're running perf tests correctly (see perfTests/README.md)")
    )

  private lazy val userCount = getParam("user-count").toInt
  // Scenarios

  def scenario_post_string(duration: FiniteDuration, routeNumber: Int): PopulationBuilder = {
    val httpProtocol = http.baseUrl(baseUrl)
    val body = new String(randomAlphanumByteArray(256))
    val execHttpPost = exec(
      http(s"HTTP POST /path$routeNumber/4")
        .post(s"/path$routeNumber/4")
        .body(StringBody(body))
    )

    scenario(s"Repeatedly invoke POST with short string body")
      .during(duration.toSeconds.toInt)(execHttpPost)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)
  
  }
  def scenario_post_bytes(duration: FiniteDuration, routeNumber: Int): PopulationBuilder = {
    val httpProtocol = http.baseUrl(baseUrl)
    val execHttpPost = exec(
      http(s"HTTP POST /path$routeNumber/4")
        .post(s"/path$routeNumber/4")
        .body(ByteArrayBody(randomByteArray(256)))
    )

    scenario(s"Repeatedly invoke POST with short byte array body")
      .during(duration.toSeconds.toInt)(execHttpPost)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)
  }

  def scenario_post_file(duration: FiniteDuration, routeNumber: Int): PopulationBuilder = {
    val httpProtocol = http.baseUrl(baseUrl)
    val execHttpPost = exec(
      http(s"HTTP POST /pathFile$routeNumber/4")
        .post(s"/pathFile$routeNumber/4")
        .body(ByteArrayBody(constRandomBytes))
        .header("Content-Type", "application/octet-stream")
    )

    scenario(s"Repeatedly invoke POST with file body")
      .during(duration.toSeconds.toInt)(execHttpPost)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)
  }

  def scenario_post_long_bytes(duration: FiniteDuration, routeNumber: Int): PopulationBuilder = {
    val httpProtocol = http.baseUrl(baseUrl)
    val execHttpPost = exec(
      http(s"HTTP POST /pathBytes$routeNumber/4")
        .post(s"/pathBytes$routeNumber/4")
        .body(ByteArrayBody(constRandomBytes))
        .header("Content-Type", "application/octet-stream")
    )

    scenario(s"Repeatedly invoke POST with large byte array")
      .during(duration.toSeconds.toInt)(execHttpPost)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)
  }

  def scenario_post_long_string(duration: FiniteDuration, routeNumber: Int): PopulationBuilder = {
    val httpProtocol = http.baseUrl(baseUrl)
    val execHttpPost = exec(
      http(s"HTTP POST /path$routeNumber/4")
        .post(s"/path$routeNumber/4")
        .body(ByteArrayBody(constRandomAlphanumBytes))
        .header("Content-Type", "application/octet-stream")
    )

    scenario(s"Repeatedly invoke POST with large byte array, interpreted to a String")
      .during(duration.toSeconds.toInt)(execHttpPost)
      .inject(atOnceUsers(userCount))
      .protocols(httpProtocol)
  }
}

abstract class TapirPerfTestSimulation extends Simulation {

  implicit val ioRuntime: IORuntime = IORuntime.global
  val serverNameParam = CommonSimulations.getParam("serv-name")
  val serverName = s"sttp.tapir.perf.${serverNameParam}Server"

  val runtimeMirror = universe.runtimeMirror(getClass.getClassLoader)
  val serverStartAction: IO[ServerRunner.KillSwitch] = try { 
    val moduleSymbol = runtimeMirror.staticModule(serverName) 
    val moduleMirror = runtimeMirror.reflectModule(moduleSymbol)
    val instance: ServerRunner = moduleMirror.instance.asInstanceOf[ServerRunner]
    instance.start
  } catch {
    case e: Throwable =>
      e.printStackTrace()
      println(s"ERROR! Could not find object $serverName or it doesn't extend ServerRunner")
      sys.exit(-2)
  }
  var killSwitch: ServerRunner.KillSwitch = IO.unit

  before({
    println("Starting http server...")
    killSwitch = serverStartAction.unsafeRunSync()
  })
  after({
    println("Shutting down http server ...")
    killSwitch.unsafeRunSync()
  })
}

class SimpleGetSimulation extends TapirPerfTestSimulation {
  setUp(CommonSimulations.simple_get(1.minute, 0))
}

class SimpleGetMultiRouteSimulation extends TapirPerfTestSimulation {
  setUp(CommonSimulations.simple_get(1.minute, 127))
}

class PostStringSimulation extends TapirPerfTestSimulation {
  setUp(CommonSimulations.scenario_post_string(10.seconds, 0))
}

class PostLongStringSimulation extends TapirPerfTestSimulation {
  setUp(CommonSimulations.scenario_post_long_string(1.minute, 0))
}
