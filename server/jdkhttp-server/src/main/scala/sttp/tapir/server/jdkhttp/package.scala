package sttp.tapir.server

import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint

import java.io.InputStream
import scala.reflect.ClassTag

package object jdkhttp {

  type JdkHttpResponseBody = (InputStream, Option[Long])

  type Id[A] = A

  implicit class IdEndpointOps[A, I, E, O, R](private val endpoint: Endpoint[A, I, E, O, R]) extends AnyVal {
    def handle(f: I => Id[Either[E, O]])(implicit aIsUnit: A =:= Unit): ServerEndpoint.Full[Unit, Unit, I, E, O, R, Id] =
      endpoint.serverLogic[Id](f)

    def handleSuccess(f: I => Id[O])(implicit aIsUnit: A =:= Unit): ServerEndpoint.Full[Unit, Unit, I, E, O, R, Id] =
      endpoint.serverLogicSuccess(f)

    def handleError(f: I => Id[E])(implicit aIsUnit: A =:= Unit): ServerEndpoint.Full[Unit, Unit, I, E, O, R, Id] =
      endpoint.serverLogicError(f)

    def handlePure(f: I => Either[E, O])(implicit aIsUnit: A =:= Unit): ServerEndpoint.Full[Unit, Unit, I, E, O, R, Id] =
      endpoint.serverLogicPure(f)

    def handleRecoverErrors(f: I => Id[O])(implicit
        eIsThrowable: E <:< Throwable,
        eClassTag: ClassTag[E],
        aIsUnit: A =:= Unit
    ): ServerEndpoint.Full[Unit, Unit, I, E, O, R, Id] =
      endpoint.serverLogicRecoverErrors(f)

    def handleOption(
        f: I => Id[Option[O]]
    )(implicit aIsUnit: A =:= Unit, eIsUnit: E =:= Unit): ServerEndpoint.Full[Unit, Unit, I, Unit, O, R, Id] =
      endpoint.serverLogicOption(f)
  }
}
