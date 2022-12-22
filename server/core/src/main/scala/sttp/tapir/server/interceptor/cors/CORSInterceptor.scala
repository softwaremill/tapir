package sttp.tapir.server.interceptor.cors

import sttp.model.{Header, HeaderNames, Method}
import sttp.monad.MonadError
import sttp.monad.syntax.MonadErrorOps
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult.Response
import sttp.tapir.server.interceptor.cors.CORSConfig._
import sttp.tapir.server.interceptor.{EndpointInterceptor, RequestHandler, RequestInterceptor, RequestResult, Responder}
import sttp.tapir.server.model.ServerResponse

class CORSInterceptor[F[_]] private (config: CORSConfig) extends RequestInterceptor[F] {
  override def apply[R, B](
      responder: Responder[F, B],
      requestHandler: EndpointInterceptor[F] => RequestHandler[F, R, B]
  ): RequestHandler[F, R, B] =
    new RequestHandler[F, R, B] {
      override def apply(request: ServerRequest, endpoints: List[ServerEndpoint[R, F]])(implicit me: MonadError[F]): F[RequestResult[B]] = {
        val handler = request.header(HeaderNames.Origin).map(cors).getOrElse(nonCors(_, _))
        handler(request, endpoints)
      }

      private val next = requestHandler(EndpointInterceptor.noop)

      private def cors(
          origin: String
      )(request: ServerRequest, endpoints: List[ServerEndpoint[R, F]])(implicit me: MonadError[F]): F[RequestResult[B]] = {
        def headerValues(rawHeader: String): Set[String] = rawHeader.split("\\s*,\\s*").toSet

        def preflight(requestedHeaderNames: Set[String], requestedMethodName: String): F[RequestResult[B]] = {
          val requestedMethod = Method.safeApply(requestedMethodName) match {
            case Left(_)       => None
            case Right(method) => Some(method)
          }

          val responseHeaders = List(
            ResponseHeaders.allowOrigin(origin),
            requestedMethod.flatMap(ResponseHeaders.allowMethods),
            ResponseHeaders.allowHeaders(requestedHeaderNames),
            ResponseHeaders.allowCredentials,
            ResponseHeaders.maxAge,
            ResponseHeaders.varyPreflight
          ).flatten

          me.unit(Response(ServerResponse(config.preflightResponseStatusCode, responseHeaders, None, None)))
        }

        def nonPreflight: F[RequestResult[B]] = {
          val responseHeaders = List(
            ResponseHeaders.allowOrigin(origin),
            ResponseHeaders.allowCredentials,
            ResponseHeaders.exposeHeaders,
            ResponseHeaders.varyNonPreflight
          ).flatten

          next(request, endpoints).map {
            case Response(serverResponse) => Response(serverResponse.addHeaders(responseHeaders))
            case failure                  => failure
          }
        }

        request.method match {
          case Method.OPTIONS =>
            request.header(HeaderNames.AccessControlRequestMethod) match {
              case Some(requestedMethodName) =>
                val requestedHeaderNames = request.header(HeaderNames.AccessControlRequestHeaders).map(headerValues).getOrElse(Set.empty)
                preflight(requestedHeaderNames, requestedMethodName)
              case None =>
                nonPreflight
            }
          case _ => nonPreflight
        }
      }

      private def nonCors(request: ServerRequest, endpoints: List[ServerEndpoint[R, F]])(implicit
          monad: MonadError[F]
      ): F[RequestResult[B]] = next(request, endpoints)
    }

  private[cors] object ResponseHeaders {
    private val Wildcard = "*"
    private val AnyMethod = Method(Wildcard)
    private val AllowAnyOrigin = Header.accessControlAllowOrigin(Wildcard)
    private val AllowAnyHeaders = Header.accessControlAllowHeaders(Wildcard)
    private val ExposeAllHeaders = Header.accessControlExposeHeaders(Wildcard)

    def allowOrigin(origin: String): Option[Header] = config.allowedOrigin match {
      case AllowedOrigin.All => Some(AllowAnyOrigin)
      case AllowedOrigin.Single(allowedOrigin) if origin.equalsIgnoreCase(allowedOrigin.toString) =>
        Some(Header.accessControlAllowOrigin(origin))
      case AllowedOrigin.Matching(predicate) if predicate(origin) =>
        Some(Header.accessControlAllowOrigin(origin))
      case _ => None
    }

    def allowCredentials: Option[Header] = config.allowedCredentials match {
      case AllowedCredentials.Allow => Some(Header.accessControlAllowCredentials(true))
      case AllowedCredentials.Deny  => None
    }

    def allowMethods(method: Method): Option[Header] = config.allowedMethods match {
      case AllowedMethods.All                                           => Some(Header.accessControlAllowMethods(AnyMethod))
      case AllowedMethods.Some(methods) if methods.exists(_.is(method)) => Some(Header.accessControlAllowMethods(methods.toList: _*))
      case _                                                            => None
    }

    def allowHeaders(requestHeaderNames: Set[String]): Option[Header] = config.allowedHeaders match {
      case AllowedHeaders.All               => Some(AllowAnyHeaders)
      case AllowedHeaders.Some(headerNames) => Some(Header.accessControlAllowHeaders(headerNames.toList: _*))
      case AllowedHeaders.Reflect           => Some(Header.accessControlAllowHeaders(requestHeaderNames.toList: _*))
    }

    def exposeHeaders: Option[Header] = config.exposedHeaders match {
      case ExposedHeaders.All               => Some(ExposeAllHeaders)
      case ExposedHeaders.Some(headerNames) => Some(Header.accessControlExposeHeaders(headerNames.toList: _*))
      case ExposedHeaders.None              => None
    }

    def maxAge: Option[Header] = config.maxAge match {
      case MaxAge.Some(duration) => Some(Header.accessControlMaxAge(duration.toSeconds))
      case MaxAge.Default        => None
    }

    def varyPreflight: Option[Header] = {
      val origin = config.allowedOrigin match {
        case AllowedOrigin.All                                   => Nil
        case AllowedOrigin.Single(_) | AllowedOrigin.Matching(_) => List(HeaderNames.Origin)
      }

      val methods = config.allowedMethods match {
        case AllowedMethods.All     => Nil
        case AllowedMethods.Some(_) => List(HeaderNames.AccessControlRequestMethod)
      }

      val headers = config.allowedHeaders match {
        case AllowedHeaders.All                              => Nil
        case AllowedHeaders.Some(_) | AllowedHeaders.Reflect => List(HeaderNames.AccessControlRequestHeaders)
      }

      origin ++ methods ++ headers match {
        case Nil         => None
        case headerNames => Some(Header.vary(headerNames: _*))
      }
    }

    def varyNonPreflight: Option[Header] = config.allowedOrigin match {
      case AllowedOrigin.All                                   => None
      case AllowedOrigin.Single(_) | AllowedOrigin.Matching(_) => Some(Header.vary(HeaderNames.Origin))
    }
  }
}

object CORSInterceptor {
  def default[F[_]]: CORSInterceptor[F] = new CORSInterceptor[F](CORSConfig.default)

  def customOrThrow[F[_]](customConfig: CORSConfig): CORSInterceptor[F] =
    if (customConfig.isValid) new CORSInterceptor[F](customConfig)
    else
      throw new IllegalArgumentException(
        "Illegal CORS config. For security reasons, when allowCredentials is set to Allow, none of: allowOrigin, allowHeaders, allowMethods can be set to All."
      )
}
