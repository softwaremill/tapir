package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.implicits._
import org.scalatest.matchers.should.Matchers._
import sttp.client4._
import sttp.model._
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.interceptor.log.DefaultServerLog
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.tests._

import java.util.concurrent.atomic.AtomicInteger

class ServerOptionsTests[F[_], OPTIONS, ROUTE](
    createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE],
    serverInterpreter: TestServerInterpreter[F, Any, OPTIONS, ROUTE]
)(implicit
    m: MonadError[F]
) {
  import createServerTest._
  import serverInterpreter._

  def tests(): List[Test] = List(
    // we need at least two endpoints for the reject interceptor to be enabled
    testServer(
      "returns tapir-generated 404 when defaultHandlers(notFoundWhenRejected = true) is used",
      NonEmptyList.of(
        route(
          List(
            endpoint.in("p1").out(stringBody).serverLogic(_ => pureResult(s"ok1".asRight[Unit])),
            endpoint.in("p2").out(stringBody).serverLogic(_ => pureResult(s"ok2".asRight[Unit]))
          ),
          (_: CustomiseInterceptors[F, OPTIONS])
            .defaultHandlers(e => ValuedEndpointOutput(stringBody, "ERROR: " + e), notFoundWhenRejected = true)
        )
      )
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/incorrect").send(backend).map { response =>
        response.code shouldBe StatusCode.NotFound
        response.body shouldBe Left("ERROR: Not Found")
      }
    }, {
      // https://github.com/softwaremill/tapir/issues/2827
      val logHandledCounter = new AtomicInteger(0)
      val logErrorCounter = new AtomicInteger(0)
      val incLogHandled: (String, Option[Throwable]) => F[Unit] = (_, _) =>
        m.eval {
          val _ = logHandledCounter.incrementAndGet()
        }
      val incLogException: (String, Throwable) => F[Unit] = (_, _) =>
        m.eval {
          val _ = logErrorCounter.incrementAndGet()
        }
      val silentEndpoint1 = endpoint.get
        .in("silent_hello_2827")
        .out(stringBody)

      val silentEndpoint2 = endpoint.get
        .in("silent_hello2_2827")
        .out(stringBody)

      val serverLog = DefaultServerLog(
        doLogWhenReceived = _ => m.unit(()),
        doLogAllDecodeFailures = (_, _) => m.unit(()),
        noLog = m.unit(()),
        doLogWhenHandled = incLogHandled,
        doLogExceptions = incLogException
      )
        .ignoreEndpoints(Seq(silentEndpoint1, silentEndpoint2))
      testServer(
        "Log events with provided functions, skipping certain endpoints",
        NonEmptyList.of(
          route(
            List(
              endpoint.get
                .in("hello_2827")
                .out(stringBody)
                .serverLogic[F](_ => pureResult("result-hello".asRight[Unit])),
              silentEndpoint1
                .serverLogic[F](_ => pureResult("result-silent_hello".asRight[Unit])),
              silentEndpoint2
                .serverLogic[F](_ => m.error(new Exception("Boom!")))
            ),
            (_: CustomiseInterceptors[F, OPTIONS])
              .serverLog(serverLog)
          )
        )
      ) { (backend, baseUri) =>
        basicStringRequest.get(uri"$baseUri/hello_2827").send(backend).map { _ =>
          logHandledCounter.get() shouldBe 1
        } >>
          basicStringRequest.get(uri"$baseUri/silent_hello_2827").send(backend).map { _ =>
            logHandledCounter.get() shouldBe 1
            logErrorCounter.get() shouldBe 0
          } >>
          basicStringRequest.get(uri"$baseUri/silent_hello2_2827").send(backend).map { _ =>
            logHandledCounter.get() shouldBe 1
            logErrorCounter.get() shouldBe 1
          } >>
          basicStringRequest.get(uri"$baseUri/hello_2827").send(backend).map { _ =>
            logHandledCounter.get() shouldBe 2
            logErrorCounter.get() shouldBe 1
          }
      }
    }
  )
}
