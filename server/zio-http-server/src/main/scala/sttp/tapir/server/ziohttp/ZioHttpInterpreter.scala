package sttp.tapir.server.ziohttp

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.model.{Header => SttpHeader}
import sttp.monad.MonadError
import sttp.tapir.EndpointInput
import sttp.tapir.internal.RichEndpointInput
import sttp.tapir.server.EndpointBodyVerifier
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.ServerInterpreter
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.ztapir._
import zio._
import zio.http.codec.{PathCodec, SegmentCodec}
import zio.http.{Header => ZioHttpHeader, Headers => ZioHttpHeaders, _}
import scala.util.chaining._

trait ZioHttpInterpreter[R] {
  def zioHttpServerOptions: ZioHttpServerOptions[R] = ZioHttpServerOptions.default

  def toHttp[R2](se: ZServerEndpoint[R2, ZioStreams with WebSockets]): Routes[R & R2, Response] =
    toHttp(List(se))

  def toHttp[R2](ses: List[ZServerEndpoint[R2, ZioStreams with WebSockets]]): Routes[R & R2, Response] = {
    EndpointBodyVerifier.throwOnErrors(EndpointBodyVerifier.verify(ses.map(_.endpoint)))

    implicit val bodyListener: ZioHttpBodyListener[R & R2] = new ZioHttpBodyListener[R & R2]
    implicit val monadError: MonadError[RIO[R & R2, *]] = new RIOMonadError[R & R2]
    val widenedSes = ses.map(_.widen[R & R2])
    val widenedServerOptions = zioHttpServerOptions.widen[R & R2]
    val zioHttpRequestBody = new ZioHttpRequestBody(widenedServerOptions)
    val zioHttpResponseBody = new ZioHttpToResponseBody(zioHttpServerOptions.inputStreamChunkSize)
    val interceptors = RejectInterceptor.disableWhenSingleEndpoint(widenedServerOptions.interceptors, widenedSes)

    def handleRequest(
        req: Request,
        filteredEndpoints: List[ZServerEndpoint[R & R2, ZioStreams with WebSockets]],
        contextPathSegments: Int
    ) =
      Handler.fromZIO {
        val interpreter = new ServerInterpreter[ZioStreams with WebSockets, RIO[R & R2, *], ZioResponseBody, ZioStreams](
          _ => filteredEndpoints,
          zioHttpRequestBody,
          zioHttpResponseBody,
          interceptors,
          zioHttpServerOptions.deleteFile
        )
        val serverRequest = ZioHttpServerRequest(req).contextPathSegments(contextPathSegments)

        interpreter
          .apply(serverRequest)
          .foldCauseZIO(
            // Interrupt-only causes are expected connection lifecycle events (client disconnect, idle timeout,
            // graceful shutdown), not application errors, so they are logged at DEBUG rather than ERROR.
            cause =>
              if (cause.isInterruptedOnly)
                ZIO.logDebugCause("Request interrupted", cause) *> ZIO.fail(Response.internalServerError("Request interrupted"))
              else
                ZIO.logErrorCause(cause) *> ZIO.fail(Response.internalServerError(cause.squash.getMessage)),
            {
              case RequestResult.Response(resp, _) =>
                resp.body match {
                  case None              => handleHttpResponse(resp, None)
                  case Some(Right(body)) => handleHttpResponse(resp, Some(body))
                  case Some(Left(body))  => handleWebSocketResponse(body, zioHttpServerOptions.customWebSocketConfig(serverRequest))
                }

              case RequestResult.Failure(_) => ZIO.succeed(Response.notFound)
            }
          )
      }

    /** A zio-http route pattern, together with the number of path segments it matches (`fixedSegments`, not counting the wildcard). The
      * count is needed to compute how many leading path segments come from zio-http route nesting (`Routes.nest`), which prepends segments
      * to the pattern, but leaves the request untouched.
      */
    sealed trait PatternWithShape { def fixedSegments: Int }
    object PatternWithShape {

      /** A pattern which ends with a wildcard, capturing whatever follows the fixed segments. */
      case class Wildcard(pattern: RoutePattern[Any], fixedSegments: Int) extends PatternWithShape

      /** A pattern which matches exactly `fixedSegments` segments. */
      case class Exact(pattern: RoutePattern[Any], fixedSegments: Int) extends PatternWithShape
    }

    /** The number of path segments matched by the pattern; the wildcard, which matches any number of them, is not counted. */
    def fixedSegmentsOf(p: RoutePattern[_]): Int =
      p.pathCodec.segments.count(s => s.nonEmpty && s != SegmentCodec.Trailing)

    def endWithWildcard(p: RoutePattern[Any]): PatternWithShape.Wildcard = {
      val withWildcard = (p / PathCodec.trailing).asInstanceOf[RoutePattern[Any]]
      PatternWithShape.Wildcard(withWildcard, fixedSegmentsOf(withWildcard))
    }

    // here we'll keep the endpoint together with the meta-data needed to create the zio-http routing information
    case class ServerEndpointWithPattern(
        index: Int,
        pathTemplate: Vector[String],
        routePattern: PatternWithShape,
        endpoint: ZServerEndpoint[R & R2, ZioStreams with WebSockets]
    )

    def toPattern(se: ZServerEndpoint[R & R2, ZioStreams with WebSockets], index: Int): ServerEndpointWithPattern = {
      val e = se.endpoint
      val inputs = e.securityInput.and(e.input).asVectorOfBasicInputs()

      // Creating the path template - no-trailing-slash inputs are treated as wildcard inputs, as they are usually
      // accompanied by endpoints which handle wildcard path inputs, when the `/` is present (to serve files). They
      // need to end up in the same group (see below), so that they are disambiguated by Tapir's logic.
      val pathTemplate = inputs.foldLeft(Vector.empty[String]) { case (p, component) =>
        component match {
          case _: EndpointInput.PathCapture[_]                                                                   => p :+ "?"
          case _: EndpointInput.PathsCapture[_]                                                                  => p :+ "..."
          case i: EndpointInput.ExtractFromRequest[_] if i.attribute(NoTrailingSlash.Attribute).getOrElse(false) => p :+ "..."
          case i: EndpointInput.FixedPath[_]                                                                     => p :+ s"{${i.s}}"
          case _                                                                                                 => p
        }
      }

      val hasPath = inputs.exists {
        case _: EndpointInput.PathCapture[_]  => true
        case _: EndpointInput.PathsCapture[_] => true
        case _: EndpointInput.FixedPath[_]    => true
        case _                                => false
      }
      val hasNoTrailingSlash = inputs.exists {
        case i: EndpointInput.ExtractFromRequest[_] if i.attribute(NoTrailingSlash.Attribute).getOrElse(false) => true
        case _                                                                                                 => false
      }

      val emptyPattern = RoutePattern(Method.ANY, PathCodec.empty).asInstanceOf[RoutePattern[Any]]

      val routePattern: PatternWithShape = if (hasPath) {
        // The second tuple parameter specifies if a wildcard should be added to the route's pattern. It can
        // be added either because of a PathsCapture, or because of an noTrailingSlash input.
        val (p, addWildcard) = inputs
          .foldLeft((emptyPattern, hasNoTrailingSlash)) { case ((p, addWildcard), component) =>
            component match {
              case i: EndpointInput.PathCapture[_] =>
                ((p / PathCodec.string(i.name.getOrElse("?"))).asInstanceOf[RoutePattern[Any]], addWildcard)
              case _: EndpointInput.PathsCapture[_] => (p, true)
              case i: EndpointInput.FixedPath[_]    => (p / PathCodec.literal(i.s), addWildcard)
              case _                                => (p, addWildcard)
            }
          }

        if (addWildcard) endWithWildcard(p) else PatternWithShape.Exact(p, fixedSegmentsOf(p))
      } else {
        // if there are no path inputs, we return a catch-all
        endWithWildcard(emptyPattern)
      }

      ServerEndpointWithPattern(index, pathTemplate, routePattern, se)
    }

    /** `t1` and `t2` are both path templates as created by `toPattern` above. Each path template is a vector of: ? | ... | {string}. This
      * method checks if `t1` is at least as general as `t2`, that is if each request that matches `t2` also matches `t1`
      */
    def isAtLeastAsGeneralAs(t1: Vector[String], t2: Vector[String]): Boolean = (t1, t2) match {
      case ("..." +: _, _)              => true
      case (_, "..." +: _)              => false
      case ("?" +: tail1, "?" +: tail2) => isAtLeastAsGeneralAs(tail1, tail2)
      case ("?" +: tail1, _ +: tail2)   => isAtLeastAsGeneralAs(tail1, tail2)
      case (_ +: _, "?" +: _)           => false
      case (p1 +: tail1, p2 +: tail2)   => (p1 == p2) && isAtLeastAsGeneralAs(tail1, tail2)
      case (Vector(), Vector())         => true
      case _                            => false
    }

    /** For each server endpoint, find the most general template among all the templates in the list, and use it for the endpoint, along
      * with the `RoutePattern` corresponding to that template.
      */
    def generaliseTemplates(endpoints: List[ServerEndpointWithPattern]): List[ServerEndpointWithPattern] = {
      // de-duplicating the path templates
      val allTemplates: List[(Vector[String], PatternWithShape)] = endpoints.map(se => (se.pathTemplate, se.routePattern)).toMap.toList
      endpoints.map { se =>
        val mostGeneral: (Vector[String], PatternWithShape) =
          allTemplates.foldLeft((se.pathTemplate, se.routePattern)) {
            case ((mostGeneralTemplate, mostGeneralPattern), (template, pattern)) =>
              if (template != mostGeneralTemplate && isAtLeastAsGeneralAs(template, mostGeneralTemplate)) {
                (template, pattern)
              } else {
                (mostGeneralTemplate, mostGeneralPattern)
              }
          }
        se.copy(pathTemplate = mostGeneral._1, routePattern = mostGeneral._2)
      }
    }

    // Generating a path tempalte for each endpoint, and then finding the most general template among all of the
    // endpoints. Once this is done, grouping the endpoints by path template. This way, if there are multiple endpoints
    // with/without trailing slash or with path wildcards, they will end up in the same group, and they will be
    // disambiguated by the Tapir logic. That's because there's no way currently to create a zio-http route pattern
    // which would match on no-trailing-slashes. A group also includes multiple endpoints with different methods, but
    // same path.
    val widenedSesGroupedByPathTemplate =
      widenedSes.zipWithIndex
        .map { case (se, index) => toPattern(se, index) }
        .pipe(generaliseTemplates)
        .groupBy(_.pathTemplate)
        .toList
        .map(_._2)
        // we try to maintain the order of endpoints as passed by the user; this order might be changed if there are
        // endpoints with/without trailing slashes, or with different methods, which are not passed as subsequent
        // values in the original `ses` list
        .sortBy(_.map(_.index).min)

    val handlers: List[Route[R & R2, Response]] = widenedSesGroupedByPathTemplate.map { sesWithPattern =>
      val endpoints = sesWithPattern.sortBy(_.index).map(_.endpoint)

      // The routes might be nested under a prefix by the user (`Routes.nest`, `PathCodec./`), which prepends segments
      // to the pattern, but leaves the request as-is. Those segments are not part of the paths described by the
      // endpoints, so they have to be skipped when matching. Their number can only be computed per-request, as the
      // pattern that the route ends up being registered under is not known when the route is created.
      // The pattern that we generate should be the same for all endpoints in a group.
      sesWithPattern.head.routePattern match {
        case PatternWithShape.Wildcard(pattern, fixedSegments) =>
          // the wildcard consumes everything after the prefix and the fixed segments, so its length is needed as well
          Route.handled[Any, R & R2](pattern)(Handler.fromFunctionHandler { (in: (Any, Request)) =>
            val (params, request) = in
            handleRequest(request, endpoints, pathSegmentCount(request.url.path) - fixedSegments - wildcardSegmentCount(params))
          })
        case PatternWithShape.Exact(pattern, fixedSegments) =>
          Route.handledIgnoreParams(pattern)(Handler.fromFunctionHandler { (request: Request) =>
            handleRequest(request, endpoints, pathSegmentCount(request.url.path) - fixedSegments)
          })
      }
    }

    Routes(Chunk.fromIterable(handlers))
  }

  /** The number of path segments, counted the same way as zio-http's route patterns match them: leading and trailing slashes are stored as
    * flags, and empty segments are dropped when parsing a path, so every segment counts.
    */
  private def pathSegmentCount(path: Path): Int = path.segments.length

  /** The number of path segments captured by the wildcard which ends the route's pattern, given the values decoded from the path. The
    * wildcard is the last of them, or the only one, if the pattern captures nothing else. Typing the pattern as `RoutePattern[Path]`
    * instead wouldn't work: the `Combiner` which would then discard the other captured values is chosen (and specialised) for `Unit`, while
    * at runtime they are a tuple.
    */
  private def wildcardSegmentCount(params: Any): Int = {
    val wildcard = params match {
      case p: Path    => p // `Path` is a case class, hence this case has to come first
      case p: Product => p.productElement(p.productArity - 1).asInstanceOf[Path]
      case _          => Path.empty // can't happen: the pattern always captures at least the wildcard
    }
    pathSegmentCount(wildcard)
  }

  private def handleWebSocketResponse(
      webSocketHandler: WebSocketHandler,
      webSocketConfig: Option[WebSocketConfig]
  ): ZIO[Any, Nothing, Response] = {
    val app = Handler.webSocket { channel =>
      for {
        channelEventsQueue <- zio.Queue.unbounded[WebSocketChannelEvent]
        messageReceptionFiber <- channel.receiveAll { message => channelEventsQueue.offer(message) }.fork
        webSocketStream <- webSocketHandler(stream.ZStream.fromQueue(channelEventsQueue))
        _ <- webSocketStream
          .mapZIO(channel.send)
          .runDrain
          .resurrect
          .catchAll { e =>
            channel.send(ChannelEvent.Read(WebSocketFrame.Close(1011, Some("Internal server error")))) *> ZIO.logErrorCause(
              "Exception when handling a WebSocket",
              Cause.fail(e)
            )
          }
      } yield messageReceptionFiber.join
    }
    webSocketConfig.fold(app)(app.withConfig).toResponse
  }

  private def handleHttpResponse(
      resp: ServerResponse[ZioResponseBody],
      body: Option[ZioHttpResponseBody]
  ): UIO[Response] = {
    val baseHeaders = resp.headers.groupBy(_.name).flatMap(sttpToZioHttpHeader).toList
    val allHeaders = body.flatMap(_.contentLength) match {
      case Some(contentLength) if resp.contentLength.isEmpty => ZioHttpHeader.ContentLength(contentLength) :: baseHeaders
      case _                                                 => baseHeaders
    }
    val statusCode = resp.code.code

    body
      .map {
        case ZioStreamHttpResponseBody(stream, Some(contentLength)) => ZIO.succeed(Body.fromStream(stream, contentLength))
        case ZioStreamHttpResponseBody(stream, None)                => ZIO.succeed(Body.fromStreamChunked(stream))
        case ZioMultipartHttpResponseBody(formFields)               => Body.fromMultipartFormUUID(Form(Chunk.fromIterable(formFields)))
        case ZioRawHttpResponseBody(chunk, _)                       => ZIO.succeed(Body.fromChunk(chunk))
      }
      .getOrElse(ZIO.succeed(Body.empty))
      .map(zioBody => Response(status = Status.fromInt(statusCode), headers = ZioHttpHeaders(allHeaders), body = zioBody))
  }

  private def sttpToZioHttpHeader(hl: (String, Seq[SttpHeader])): Seq[ZioHttpHeader] = {
    hl._1.toLowerCase match {
      case "set-cookie" =>
        hl._2.map(_.value).map { rawValue =>
          ZioHttpHeader.SetCookie.parse(rawValue).toOption.getOrElse {
            ZioHttpHeader.Custom(hl._1, rawValue)
          }
        }
      case _ => List(ZioHttpHeader.Custom(hl._1, hl._2.map(_.value).mkString(", ")))
    }
  }
}

object ZioHttpInterpreter {

  def apply[R](serverOptions: ZioHttpServerOptions[R]): ZioHttpInterpreter[R] =
    new ZioHttpInterpreter[R] {
      override def zioHttpServerOptions: ZioHttpServerOptions[R] = serverOptions
    }
  def apply(): ZioHttpInterpreter[Any] =
    new ZioHttpInterpreter[Any] {
      override def zioHttpServerOptions: ZioHttpServerOptions[Any] = ZioHttpServerOptions.default[Any]
    }
}
