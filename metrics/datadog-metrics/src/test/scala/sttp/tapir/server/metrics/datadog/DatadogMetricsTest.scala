package sttp.tapir.server.metrics.datadog

import com.timgroup.statsd._
import org.scalatest.Retries.isRetryable
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.tagobjects.Retryable
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Canceled, Failed, Outcome}
import sttp.tapir.TestUtil._
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.TestUtil._
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}
import sttp.tapir.server.metrics.MetricLabels
import sttp.tapir.server.metrics.datadog.DatadogMetricsTest._

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.charset.StandardCharsets
import java.time._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success, Try}

class DatadogMetricsTest extends AnyFlatSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll {

  private val statsdServerPort = 17254
  private val statsdServer = new MockStatsDServer(statsdServerPort)

  override def beforeAll(): Unit = statsdServer.start()
  override def afterAll(): Unit = statsdServer.close()

  before {
    statsdServer.clear()
  }

  after {
    statsdServer.clear()
  }

  // some tests are timing-dependent and sometimes fail
  // https://stackoverflow.com/questions/22799495/scalatest-running-a-test-50-times
  val retries = 5

  override def withFixture(test: NoArgTest): Outcome = {
    if (isRetryable(test)) withFixture(test, retries) else super.withFixture(test)
  }

  def withFixture(test: NoArgTest, count: Int): Outcome = {
    val outcome = super.withFixture(test)
    outcome match {
      case Failed(_) | Canceled(_) =>
        statsdServer.clear()
        if (count == 1) super.withFixture(test) else withFixture(test, count - 1)
      case other => other
    }
  }
  //

  "default metrics" should "collect requests active" in {
    // given
    val serverEp = PersonsApi { name =>
      Thread.sleep(2000)
      PersonsApi.defaultLogic(name)
    }.serverEp
    val client =
      new NonBlockingStatsDClientBuilder()
        .hostname("localhost")
        .port(statsdServerPort)
        .build()

    val metrics = DatadogMetrics[Id](client).addRequestsActive()
    val interpreter =
      new ServerInterpreter[Any, Id, Unit, NoStreams](
        _ => List(serverEp),
        TestRequestBody,
        UnitToResponseBody,
        List(metrics.metricsInterceptor()),
        _ => ()
      )

    // when
    val response = Future { interpreter.apply(PersonsApi.request("Jacob")) }

    waitReceiveMessage(statsdServer)

    // then
    statsdServer.getReceivedMessages should contain("""tapir.request_active.count:1|c|#method:GET,path:/person""")

    statsdServer.clear()

    ScalaFutures.whenReady(response, Timeout(Span(3, Seconds))) { _ =>
      waitReceiveMessage(statsdServer)
      statsdServer.getReceivedMessages should contain("""tapir.request_active.count:-1|c|#method:GET,path:/person""")
    }
  }

  "default metrics" should "collect requests total" in {
    // given
    val serverEp = PersonsApi().serverEp
    val client =
      new NonBlockingStatsDClientBuilder()
        .hostname("localhost")
        .port(statsdServerPort)
        .build()

    val metrics = DatadogMetrics[Id](client).addRequestsTotal()
    val interpreter =
      new ServerInterpreter[Any, Id, Unit, NoStreams](
        _ => List(serverEp),
        TestRequestBody,
        UnitToResponseBody,
        List(metrics.metricsInterceptor(), new DecodeFailureInterceptor(DefaultDecodeFailureHandler[Id])),
        _ => ()
      )

    // when
    interpreter.apply(PersonsApi.request("Jacob"))
    interpreter.apply(PersonsApi.request("Jacob"))
    interpreter.apply(PersonsApi.request("Mike"))
    interpreter.apply(PersonsApi.request(""))

    waitReceiveMessage(statsdServer)

    // then
    statsdServer.getReceivedMessages should contain("""tapir.request_total.count:2|c|#status:2xx,method:GET,path:/person""")
    statsdServer.getReceivedMessages should contain("""tapir.request_total.count:2|c|#status:4xx,method:GET,path:/person""")
  }

  "default metrics" should "collect requests duration" taggedAs Retryable in {
    // given
    val clock = new TestClock()

    val waitServerEp: Long => ServerEndpoint[Any, Id] = millis => {
      PersonsApi { name =>
        clock.forward(millis)
        PersonsApi.defaultLogic(name)
      }.serverEp
    }
    val waitBodyListener: Long => BodyListener[Id, String] = millis =>
      new BodyListener[Id, String] {
        override def onComplete(body: String)(cb: Try[Unit] => Id[Unit]): String = {
          clock.forward(millis)
          cb(Success(()))
          body
        }
      }
    val client =
      new NonBlockingStatsDClientBuilder()
        .hostname("localhost")
        .port(statsdServerPort)
        .build()

    val metrics = DatadogMetrics[Id](client).addRequestsDuration(clock = clock)
    def interpret(sleepHeaders: Long, sleepBody: Long) =
      new ServerInterpreter[Any, Id, String, NoStreams](
        _ => List(waitServerEp(sleepHeaders)),
        TestRequestBody,
        StringToResponseBody,
        List(metrics.metricsInterceptor()),
        _ => ()
      )(implicitly, waitBodyListener(sleepBody)).apply(PersonsApi.request("Jacob"))

    // when
    interpret(100, 1000)
    interpret(200, 2000)
    interpret(300, 3000)

    waitReceiveMessage(statsdServer)

    // then
    // headers
    statsdServer.getReceivedMessages should contain(
      """tapir.request_duration_seconds:0.1|h|#phase:headers,status:2xx,method:GET,path:/person"""
    )
    statsdServer.getReceivedMessages should contain(
      """tapir.request_duration_seconds:0.2|h|#phase:headers,status:2xx,method:GET,path:/person"""
    )
    statsdServer.getReceivedMessages should contain(
      """tapir.request_duration_seconds:0.3|h|#phase:headers,status:2xx,method:GET,path:/person"""
    )

    // body
    statsdServer.getReceivedMessages should contain(
      """tapir.request_duration_seconds:1.1|h|#phase:body,status:2xx,method:GET,path:/person"""
    )
    statsdServer.getReceivedMessages should contain(
      """tapir.request_duration_seconds:2.2|h|#phase:body,status:2xx,method:GET,path:/person"""
    )
    statsdServer.getReceivedMessages should contain(
      """tapir.request_duration_seconds:3.3|h|#phase:body,status:2xx,method:GET,path:/person"""
    )
  }

  "default metrics" should "customize labels" taggedAs Retryable in {
    // given
    val serverEp = PersonsApi().serverEp
    val labels = MetricLabels(forRequest = List("key" -> { case (_, _) => "value" }), forResponse = Nil)
    val client =
      new NonBlockingStatsDClientBuilder()
        .hostname("localhost")
        .port(statsdServerPort)
        .build()

    val metrics = DatadogMetrics[Id](client).addRequestsTotal(labels)
    val interpreter =
      new ServerInterpreter[Any, Id, Unit, NoStreams](
        _ => List(serverEp),
        TestRequestBody,
        UnitToResponseBody,
        List(metrics.metricsInterceptor()),
        _ => ()
      )

    // when
    interpreter.apply(PersonsApi.request("Jacob"))

    waitReceiveMessage(statsdServer)

    // then
    statsdServer.getReceivedMessages should contain("""tapir.request_total.count:1|c|#key:value""")
  }

  "metrics" should "be collected on exception when response from exception handler" taggedAs Retryable in {
    // given
    val serverEp = PersonsApi { _ => throw new RuntimeException("Ups") }.serverEp
    val client =
      new NonBlockingStatsDClientBuilder()
        .hostname("localhost")
        .port(statsdServerPort)
        .build()

    val metrics = DatadogMetrics[Id](client).addRequestsTotal()
    val interpreter = new ServerInterpreter[Any, Id, Unit, NoStreams](
      _ => List(serverEp),
      TestRequestBody,
      UnitToResponseBody,
      List(metrics.metricsInterceptor(), new ExceptionInterceptor(DefaultExceptionHandler[Id])),
      _ => ()
    )

    // when
    interpreter.apply(PersonsApi.request("Jacob"))

    waitReceiveMessage(statsdServer)

    // then
    statsdServer.getReceivedMessages should contain("""tapir.request_total.count:1|c|#status:5xx,method:GET,path:/person""")
  }
}

object DatadogMetricsTest {
  private def waitReceiveMessage(statsdServer: MockStatsDServer): Unit = {
    while (statsdServer.getReceivedMessages.count(m => !m.startsWith("datadog")) == 0) {
      Thread.sleep(100)
    }
  }

  class MockStatsDServer(port: Int) {
    private var receivedMessages: List[String] = Nil
    private val server = DatagramChannel.open()

    def clear(): Unit = receivedMessages = Nil

    def start(): Unit = {
      server.bind(new InetSocketAddress(port))

      val thread = new Thread(() => {
        val packet = ByteBuffer.allocate(1500)

        while (server.isOpen)
          try {
            packet.clear()
            server.receive(packet)
            packet.flip()

            StandardCharsets.UTF_8.decode(packet).toString.split('\n').foreach { message =>
              val trimmed = message.trim

              if (trimmed.nonEmpty)
                receivedMessages = receivedMessages :+ trimmed
            }
          } catch {
            case _: Throwable =>
          }
      })

      thread.setDaemon(true)
      thread.start()
    }

    def close(): Unit =
      try server.close()
      catch { case _: Throwable => }

    def getReceivedMessages: List[String] = receivedMessages
  }

  class TestClock(start: Long = System.currentTimeMillis()) extends Clock {
    private var _millis = start

    def forward(m: Long): Unit = {
      _millis += m
    }

    override def getZone: ZoneId = Clock.systemUTC().getZone
    override def withZone(zone: ZoneId): Clock = this
    override def instant(): Instant = Instant.ofEpochMilli(_millis)
  }
}
