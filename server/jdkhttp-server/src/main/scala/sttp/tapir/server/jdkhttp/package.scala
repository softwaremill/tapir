package sttp.tapir.server

import sttp.tapir.{Endpoint, Id}

import java.io.InputStream
import scala.reflect.ClassTag

package object jdkhttp {

  type HttpServer = com.sun.net.httpserver.HttpServer
  type HttpsConfigurator = com.sun.net.httpserver.HttpsConfigurator

  type JdkHttpResponseBody = (InputStream, Option[Long])

  implicit class IdEndpointOps[A, I, E, O, R](private val endpoint: Endpoint[A, I, E, O, R]) extends AnyVal {
    def handle(f: I => Either[E, O])(implicit aIsUnit: A =:= Unit): ServerEndpoint.Full[Unit, Unit, I, E, O, R, Id] =
      endpoint.serverLogic[Id](f)

    def handleSuccess(f: I => O)(implicit aIsUnit: A =:= Unit): ServerEndpoint.Full[Unit, Unit, I, E, O, R, Id] =
      endpoint.serverLogicSuccess[Id](f)

    def handleError(f: I => E)(implicit aIsUnit: A =:= Unit): ServerEndpoint.Full[Unit, Unit, I, E, O, R, Id] =
      endpoint.serverLogicError[Id](f)

    def handleRecoverErrors(f: I => O)(implicit
        eIsThrowable: E <:< Throwable,
        eClassTag: ClassTag[E],
        aIsUnit: A =:= Unit
    ): ServerEndpoint.Full[Unit, Unit, I, E, O, R, Id] =
      endpoint.serverLogicRecoverErrors[Id](f)

    def handleOption(
        f: I => Option[O]
    )(implicit aIsUnit: A =:= Unit, eIsUnit: E =:= Unit): ServerEndpoint.Full[Unit, Unit, I, Unit, O, R, Id] =
      endpoint.serverLogicOption[Id](f)

    def handleSecurity[PRINCIPAL](f: A => Either[E, PRINCIPAL]): PartialServerEndpoint[A, PRINCIPAL, I, E, O, R, Id] =
      endpoint.serverSecurityLogic[PRINCIPAL, Id](f)

    def handleSecuritySuccess[PRINCIPAL](
        f: A => PRINCIPAL
    ): PartialServerEndpoint[A, PRINCIPAL, I, E, O, R, Id] =
      endpoint.serverSecurityLogicSuccess[PRINCIPAL, Id](f)

    def handleSecurityError[PRINCIPAL](
        f: A => E
    ): PartialServerEndpoint[A, PRINCIPAL, I, E, O, R, Id] =
      endpoint.serverSecurityLogicError[PRINCIPAL, Id](f)

    def handleSecurityRecoverErrors[PRINCIPAL](
        f: A => PRINCIPAL
    )(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): PartialServerEndpoint[A, PRINCIPAL, I, E, O, R, Id] =
      endpoint.serverSecurityLogicRecoverErrors[PRINCIPAL, Id](f)

    def handleSecurityOption[PRINCIPAL](
        f: A => Option[PRINCIPAL]
    )(implicit eIsUnit: E =:= Unit): PartialServerEndpoint[A, PRINCIPAL, I, Unit, O, R, Id] =
      endpoint.serverSecurityLogicOption[PRINCIPAL, Id](f)

    def handleSecurityWithOutput[PRINCIPAL](
        f: A => Either[E, (O, PRINCIPAL)]
    ): PartialServerEndpointWithSecurityOutput[A, PRINCIPAL, I, E, O, Unit, R, Id] =
      endpoint.serverSecurityLogicWithOutput[PRINCIPAL, Id](f)

    def handleSecuritySuccessWithOutput[PRINCIPAL](
        f: A => (O, PRINCIPAL)
    ): PartialServerEndpointWithSecurityOutput[A, PRINCIPAL, I, E, O, Unit, R, Id] =
      endpoint.serverSecurityLogicSuccessWithOutput[PRINCIPAL, Id](f)

    def handleSecurityRecoverErrorsWithOutput[PRINCIPAL](
        f: A => (O, PRINCIPAL)
    )(implicit
        eIsThrowable: E <:< Throwable,
        eClassTag: ClassTag[E]
    ): PartialServerEndpointWithSecurityOutput[A, PRINCIPAL, I, E, O, Unit, R, Id] =
      endpoint.serverSecurityLogicRecoverErrorsWithOutput[PRINCIPAL, Id](f)

    def handleSecurityOptionWithOutput[PRINCIPAL](
        f: A => Option[(O, PRINCIPAL)]
    )(implicit eIsUnit: E =:= Unit): PartialServerEndpointWithSecurityOutput[A, PRINCIPAL, I, Unit, O, Unit, R, Id] =
      endpoint.serverSecurityLogicOptionWithOutput[PRINCIPAL, Id](f)

  }
}
