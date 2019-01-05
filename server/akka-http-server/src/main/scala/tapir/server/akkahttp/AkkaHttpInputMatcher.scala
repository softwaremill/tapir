package tapir.server.akkahttp
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.RequestContext
import tapir.internal.SeqToParams
import tapir.{DecodeResult, EndpointIO, EndpointInput}

private[akkahttp] object AkkaHttpInputMatcher {

  case class MatchResult(values: List[Any], ctx: RequestContext, canRemoveSlash: Boolean) {
    def prependValue(v: Any): MatchResult = copy(values = v :: values)
  }

  def doMatch(inputs: Vector[EndpointInput.Single[_]], ctx: RequestContext, body: Any): Option[MatchResult] = {
    doMatch(inputs, ctx, canRemoveSlash = true, body)
  }

  private def doMatch(inputs: Vector[EndpointInput.Single[_]],
                      ctx: RequestContext,
                      canRemoveSlash: Boolean,
                      body: Any): Option[MatchResult] = {

    def handleMapped[II, T](wrapped: EndpointInput[II], f: II => T, inputsTail: Vector[EndpointInput.Single[_]]): Option[MatchResult] = {
      doMatch(wrapped.asVectorOfSingle, ctx, canRemoveSlash, body).flatMap { result =>
        doMatch(inputsTail, result.ctx, result.canRemoveSlash, body).map {
          _.prependValue(f.asInstanceOf[Any => Any].apply(SeqToParams(result.values)))
        }
      }
    }

    inputs match {
      case Vector() => Some(MatchResult(Nil, ctx, canRemoveSlash))
      case EndpointInput.PathSegment(ss) +: inputsTail =>
        ctx.unmatchedPath match {
          case Uri.Path.Slash(pathTail) if canRemoveSlash =>
            doMatch(inputs, ctx.withUnmatchedPath(pathTail), canRemoveSlash = false, body)
          case Uri.Path.Segment(`ss`, pathTail) =>
            doMatch(inputsTail, ctx.withUnmatchedPath(pathTail), canRemoveSlash = true, body)
          case _ => None
        }
      case EndpointInput.PathCapture(codec, _, _, _) +: inputsTail =>
        ctx.unmatchedPath match {
          case Uri.Path.Slash(pathTail) if canRemoveSlash =>
            doMatch(inputs, ctx.withUnmatchedPath(pathTail), canRemoveSlash = false, body)
          case Uri.Path.Segment(s, pathTail) =>
            codec.decode(s) match {
              case DecodeResult.Value(v) =>
                doMatch(inputsTail, ctx.withUnmatchedPath(pathTail), canRemoveSlash = true, body).map {
                  _.prependValue(v)
                }
              case _ => None
            }
          case _ => None
        }
      case EndpointInput.Query(name, codec, _, _) +: inputsTail =>
        codec.decodeOptional(ctx.request.uri.query().get(name)) match {
          case DecodeResult.Value(v) =>
            doMatch(inputsTail, ctx, canRemoveSlash = true, body).map {
              _.prependValue(v)
            }
          case _ => None
        }
      case EndpointIO.Header(name, codec, _, _) +: inputsTail =>
        codec.decodeOptional(ctx.request.headers.find(_.is(name.toLowerCase)).map(_.value())) match {
          case DecodeResult.Value(v) =>
            doMatch(inputsTail, ctx, canRemoveSlash = true, body).map {
              _.prependValue(v)
            }
          case _ => None
        }
      case EndpointIO.Body(codec, _, _) +: inputsTail =>
        codec.decodeOptional(Some(body)) match {
          case DecodeResult.Value(v) =>
            doMatch(inputsTail, ctx, canRemoveSlash = true, body).map {
              _.prependValue(v)
            }
          case _ => None
        }
      case EndpointInput.Mapped(wrapped, f, _, _) +: inputsTail =>
        handleMapped(wrapped, f, inputsTail)
      case EndpointIO.Mapped(wrapped, f, _, _) +: inputsTail =>
        handleMapped(wrapped, f, inputsTail)
    }
  }
}
