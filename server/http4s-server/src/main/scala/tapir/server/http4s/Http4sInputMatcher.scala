package tapir.server.http4s

import cats.data.StateT
import cats.effect.Sync
import cats.implicits._
import tapir.internal.SeqToParams
import tapir.{DecodeResult, EndpointIO, EndpointInput, MultiQueryParams}

private[http4s] class Http4sInputMatcher[F[_]: Sync] {
  private val logger = org.log4s.getLogger

  def matchInputs(inputs: Vector[EndpointInput.Single[_]]): ContextState[F] = inputs match {
    case Vector() =>
      StateT(context => Either.right((context, MatchResult[F](Nil, context))))
    case EndpointInput.PathSegment(ss: String) +: inputsTail =>
      for {
        ctx <- getState
        _ <- modifyState(_.dropPath(ss.length + 1))
        doesMatch = ctx.unmatchedPath.drop(1).startsWith(ss)
        _ = logger.debug(s"$doesMatch, ${ctx.unmatchedPath}, $ss")
        r <- if (ctx.unmatchedPath.drop(1).startsWith(ss)) {
          val value: ContextState[F] = matchInputs(inputsTail)
          value
        } else {
          val value: ContextState[F] = StateT.liftF(Either.left(s"Unmatched path segment: $ss, $ctx"))
          value
        }
      } yield r
    case EndpointInput.PathCapture(codec, name, _) +: inputsTail =>
      val decodeResult: StateT[Either[Error, ?], Context[F], DecodeResult[Any]] = StateT(
        (ctx: Context[F]) =>
          nextSegment(ctx.unmatchedPath)
            .map {
              case (segment, remaining) =>
                logger.debug(s"Capturing path: $segment, remaining: $remaining, $name")
                (ctx.copy(unmatchedPath = remaining), codec.decode(segment))
          })

      decodeResult.flatMap {
        case DecodeResult.Value(v) =>
          logger.debug(s"Decoded path: $v")
          matchInputs(inputsTail).map(_.prependValue(v))
        case decodingFailure => StateT.liftF(Either.left(s"Decoding path failed: $decodingFailure"))
      }
    case EndpointInput.PathsCapture(_) +: inputsTail =>
      def allSegments(p: String, acc: Vector[String]): Vector[String] = nextSegment(p) match {
        case Left(_)                     => acc
        case Right((segment, remaining)) => allSegments(remaining, acc :+ segment)
      }

      for {
        ctx <- getState
        ps = allSegments(ctx.unmatchedPath, Vector.empty)
        _ <- modifyState(ctx => ctx.copy(unmatchedPath = ""))
        _ = logger.debug(s"Capturing paths: $ps")
        r <- matchInputs(inputsTail).map(_.prependValue(ps))
      } yield r
    case EndpointInput.Query(name, codec, _) +: inputsTail =>
      for {
        ctx <- getState
        query = codec.decodeOptional(ctx.queryParam(name))
        _ = logger.debug(s"Found query: $query, $name, ${ctx.queryParams}")
        res <- continueMatch(query, inputsTail)
      } yield res
    case EndpointInput.QueryParams(_) +: inputsTail =>
      for {
        ctx <- getState
        queryParams = MultiQueryParams.fromSeq(ctx.queryParams.toSeq)
        _ = logger.debug(s"Found query params: $queryParams")
        res <- continueMatch(DecodeResult.Value(queryParams), inputsTail)
      } yield res
    case EndpointIO.Header(name, codec, _) +: inputsTail =>
      for {
        ctx <- getState
        header = codec.decodeOptional(ctx.header(name))
        _ = logger.debug(s"Found header: $header")
        res <- continueMatch(header, inputsTail)
      } yield res
    case EndpointIO.Headers(_) +: inputsTail =>
      for {
        ctx <- getState
        headers = ctx.headers.map(h => (h.name.value, h.value)).toSeq
        _ = logger.debug(s"Found headers: $headers")
        res <- continueMatch(DecodeResult.Value(headers), inputsTail)
      } yield res
    case EndpointIO.Body(codec, _) +: inputsTail =>
      for {
        ctx <- getState
        decoded: DecodeResult[Any] = codec.decodeOptional(ctx.body)
        res <- decoded match {
          case DecodeResult.Value(_) =>
            val r: ContextState[F] = continueMatch(decoded, inputsTail)
            r
          case _ =>
            matchInputs(inputsTail)
        }
      } yield res
    case EndpointInput.Mapped(wrapped, f, _, _) +: inputsTail =>
      handleMapped(wrapped, f, inputsTail)
    case EndpointIO.Mapped(wrapped, f, _, _) +: inputsTail =>
      handleMapped(wrapped, f, inputsTail)
  }

  private def continueMatch(decodeResult: DecodeResult[Any], inputsTail: Vector[EndpointInput.Single[_]]): ContextState[F] =
    decodeResult match {
      case DecodeResult.Value(v) =>
        logger.debug(s"Continuing match: $v")
        matchInputs(inputsTail).map(_.prependValue(v))
      case err =>
        StateT.inspectF((ctx: Context[F]) => Either.left(s"${err.toString}, ${ctx.unmatchedPath}"))
    }

  private def handleMapped[II, T](wrapped: EndpointInput[II], f: II => T, inputsTail: Vector[EndpointInput.Single[_]]): ContextState[F] =
    for {
      r1 <- matchInputs(wrapped.asVectorOfSingle)
      r2 <- matchInputs(inputsTail)
        .map(_.prependValue(f.asInstanceOf[Any => Any].apply(SeqToParams(r1.values))))
    } yield r2

  private def nextSegment(unmatchedPath: String): Either[Error, (String, String)] =
    if (unmatchedPath.startsWith("/")) {
      val noSlash = unmatchedPath.drop(1)
      val lastIndex =
        if (noSlash.contains("/")) noSlash.indexOf("/")
        else if (noSlash.contains("?")) noSlash.indexOf("?")
        else
          noSlash.length
      val (segment, remaining) = noSlash.splitAt(lastIndex)
      Either.right((segment, remaining))
    } else {
      Either.left("path doesn't start with \"/\"")
    }

  private def getState: StateT[Either[Error, ?], Context[F], Context[F]] = StateT.get
  private def modifyState(f: Context[F] => Context[F]): StateT[Either[Error, ?], Context[F], Unit] =
    StateT.modify[Either[Error, ?], Context[F]](f)
}
