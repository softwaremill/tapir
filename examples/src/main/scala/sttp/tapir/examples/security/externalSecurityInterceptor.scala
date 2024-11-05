// {cat=Security; effects=Future; server=Netty}: Interceptor verifying externally added security credentials

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server:1.11.8
//> using dep com.softwaremill.sttp.client3::core:3.9.8

package sttp.tapir.examples.security

import sttp.client3.*
import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.shared.Identity
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding, NettyFutureServerOptions}
import sttp.tapir.*
import sttp.tapir.server.interceptor.{
  DecodeFailureContext,
  DecodeSuccessContext,
  EndpointHandler,
  EndpointInterceptor,
  Responder,
  SecurityFailureContext
}
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.model.{ServerResponse, ValuedEndpointOutput}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

/** An example of using an externally assigned security credential (e.g. by a sidecar or a gateway) to accept/reject requests to given
  * endpoints. The role is expected to be added to the request in the `X-Role` header. Each endpoint is associated with a list of roles
  * (using an attribute), which are allowed to access the endpoint. Verification, if the endpoint can be accessed is done using an
  * [[EndpointInterceptor]].
  */
@main def externalSecurityInterceptor(): Unit =
  // the sidecar/gateway should add the authenticated role in this header
  val roleHeader = "X-Role"

  // attribute, which is used to specify the roles that are allowed to access the endpoint
  case class AllowedRoles(roles: List[String])
  val rolesAttribute = AttributeKey[AllowedRoles]

  val secretEndpoint1 = endpoint.get
    .in("secret" / "1")
    .out(stringBody)
    .attribute(rolesAttribute, AllowedRoles(List("role1")))
    .serverLogicSuccess(_ => Future.successful("ok1"))
  val secretEndpoint2 = endpoint.get
    .in("secret" / "2")
    .out(stringBody)
    .attribute(rolesAttribute, AllowedRoles(List("role2", "role3")))
    .serverLogicSuccess(_ => Future.successful("ok2"))

  // intercepting successfully decoded endpoints - we then know, that the request matches the endpoint; in that case,
  // additionally checking the roles
  val rolesInterceptor = new EndpointInterceptor[Future] {
    override def apply[B](responder: Responder[Future, B], delegate: EndpointHandler[Future, B]): EndpointHandler[Future, B] =
      new EndpointHandler[Future, B] {
        override def onDecodeSuccess[A, U, I](
            ctx: DecodeSuccessContext[Future, A, U, I]
        )(implicit monad: MonadError[Future], bodyListener: BodyListener[Future, B]): Future[ServerResponse[B]] = {
          val externalRole = ctx.request.header(roleHeader)
          val endpointRoles = ctx.serverEndpoint.attribute(rolesAttribute)
          // when there are roles defined for the endpoint, checking if the external role is one of them
          if (endpointRoles.forall(er => externalRole.exists(r => er.roles.contains(r)))) delegate.onDecodeSuccess(ctx)
          // the required role is not present, responding with a 403
          else responder(ctx.request, ValuedEndpointOutput(statusCode, StatusCode.Forbidden))
        }

        override def onSecurityFailure[A](
            ctx: SecurityFailureContext[Future, A]
        )(implicit monad: MonadError[Future], bodyListener: BodyListener[Future, B]): Future[ServerResponse[B]] =
          delegate.onSecurityFailure(ctx)

        override def onDecodeFailure(
            ctx: DecodeFailureContext
        )(implicit monad: MonadError[Future], bodyListener: BodyListener[Future, B]): Future[Option[ServerResponse[B]]] =
          delegate.onDecodeFailure(ctx)
      }
  }

  // starting the server
  val serverBinding: NettyFutureServerBinding =
    Await.result(
      NettyFutureServer()
        .options(NettyFutureServerOptions.customiseInterceptors.prependInterceptor(rolesInterceptor).options)
        .addEndpoint(secretEndpoint1)
        .addEndpoint(secretEndpoint2)
        .start(),
      Duration.Inf
    )

  // checking if the roles are properly verified - no assert exceptions should be thrown
  try {
    val port = serverBinding.port
    val host = serverBinding.hostName
    println(s"Server started at port = ${serverBinding.port}")

    val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()

    def secretRequest(endpoint: String) = basicRequest.response(asStringAlways).get(uri"http://$host:$port/secret/$endpoint")

    assert(secretRequest("1").send(backend).code == StatusCode(403))
    assert(secretRequest("1").header("X-Role", "role1").send(backend).code == StatusCode(200))
    assert(secretRequest("1").header("X-Role", "role2").send(backend).code == StatusCode(403))
    assert(secretRequest("1").header("X-Role", "role3").send(backend).code == StatusCode(403))

    assert(secretRequest("2").send(backend).code == StatusCode(403))
    assert(secretRequest("2").header("X-Role", "role1").send(backend).code == StatusCode(403))
    assert(secretRequest("2").header("X-Role", "role2").send(backend).code == StatusCode(200))
    assert(secretRequest("2").header("X-Role", "role3").send(backend).code == StatusCode(200))
  } finally Await.result(serverBinding.stop(), Duration.Inf)
