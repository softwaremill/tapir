package tapir.server.akkahttp
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.RequestContext
import tapir.internal.SeqToParams
import tapir.{DecodeResult, EndpointIO, EndpointInput, MultiQueryParams}

private[akkahttp] object AkkaHttpInputMatcher {

  def doMatch(inputs: Vector[EndpointInput.Single[_]], req: RequestContext, body: Any): Option[List[Any]] = {
    doMatch(inputs, MatchContext(req, canRemoveSlash = true, body))
  }

  private case class MatchContext(req: RequestContext, canRemoveSlash: Boolean, body: Any)

  private def doMatch(inputs: Vector[EndpointInput.Single[_]], ctx: MatchContext): Option[List[Any]] = {

    inputs match {
      case Vector() => Some(Nil)
      case EndpointInput.PathSegment(ss) +: inputsTail =>
        ctx.req.unmatchedPath match {
          case Uri.Path.Slash(pathTail) if ctx.canRemoveSlash =>
            doMatch(inputs, ctx.copy(req = ctx.req.withUnmatchedPath(pathTail), canRemoveSlash = false)) // TODO: handle slash here?
          case Uri.Path.Segment(`ss`, pathTail) =>
            doMatch(inputsTail, ctx.copy(req = ctx.req.withUnmatchedPath(pathTail), canRemoveSlash = true))
          case _ => None
        }
      case EndpointInput.PathCapture(codec, _, _, _) +: inputsTail =>
        ctx.req.unmatchedPath match {
          case Uri.Path.Slash(pathTail) if ctx.canRemoveSlash =>
            doMatch(inputs, ctx.copy(req = ctx.req.withUnmatchedPath(pathTail), canRemoveSlash = false))
          case Uri.Path.Segment(s, pathTail) =>
            codec.decode(s) match {
              case DecodeResult.Value(v) =>
                doMatch(inputsTail, ctx.copy(req = ctx.req.withUnmatchedPath(pathTail), canRemoveSlash = true)).map(v :: _)
              case _ => None
            }
          case _ => None
        }
      case EndpointInput.Query(name, codec, _, _) +: inputsTail =>
        codec.decodeOptional(ctx.req.request.uri.query().get(name)) match {
          case DecodeResult.Value(v) =>
            doMatch(inputsTail, ctx.copy(canRemoveSlash = true)).map(v :: _)
          case _ => None
        }
      case EndpointInput.QueryParams(_, _) +: inputsTail =>
        val params = MultiQueryParams.fromSeq(ctx.req.request.uri.query())
        doMatch(inputsTail, ctx.copy(canRemoveSlash = true)).map(params :: _)
      case EndpointIO.Header(name, codec, _, _) +: inputsTail =>
        codec.decodeOptional(ctx.req.request.headers.find(_.is(name.toLowerCase)).map(_.value())) match {
          case DecodeResult.Value(v) =>
            doMatch(inputsTail, ctx.copy(canRemoveSlash = true)).map(v :: _)
          case _ => None
        }
      case EndpointIO.Body(codec, _, _) +: inputsTail =>
        codec.decodeOptional(Some(ctx.body)) match {
          case DecodeResult.Value(v) =>
            doMatch(inputsTail, ctx.copy(canRemoveSlash = true)).map(v :: _)
          case _ => None
        }
      case EndpointInput.Mapped(wrapped, f, _, _) +: inputsTail =>
        handleMapped(wrapped, f, inputsTail, ctx)
      case EndpointIO.Mapped(wrapped, f, _, _) +: inputsTail =>
        handleMapped(wrapped, f, inputsTail, ctx)
    }
  }

  private def handleMapped[II, T](wrapped: EndpointInput[II],
                                  f: II => T,
                                  inputsTail: Vector[EndpointInput.Single[_]],
                                  ctx: MatchContext): Option[List[Any]] = {
    doMatch(wrapped.asVectorOfSingle, ctx)
      .flatMap { result =>
        doMatch(inputsTail, ctx)
          .map(f.asInstanceOf[Any => Any].apply(SeqToParams(result)) :: _)
      }
  }
}
