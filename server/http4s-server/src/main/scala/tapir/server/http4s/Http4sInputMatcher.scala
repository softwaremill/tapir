package tapir.server.http4s
import cats.Applicative
import cats.data.StateT
import cats.effect.Sync
import cats.implicits._
import tapir.internal.SeqToParams
import tapir.{DecodeResult, EndpointIO, EndpointInput}

private[http4s] object Http4sInputMatcher {
  private val logger = org.log4s.getLogger

  def matchInputs[F[_]: Sync](inputs: Vector[EndpointInput.Single[_]]): ContextState[F] = inputs match {
    case Vector() =>
      StateT(context => Either.right((context, MatchResult[F](Nil, context))))
    case EndpointInput.PathSegment(ss: String) +: inputsTail =>
      for {
        ctx <- getState[F]
        _ <- modifyState[F](_.dropPath(ss.length + 1))
        doesMatch = ctx.unmatchedPath.drop(1).startsWith(ss)
        _ = logger.debug(s"$doesMatch, ${ctx.unmatchedPath}, $ss")
        r <- if (ctx.unmatchedPath.drop(1).startsWith(ss)) {
          val value: ContextState[F] = matchInputs[F](inputsTail)
          value
        } else {
          val value: ContextState[F] = StateT.liftF(Either.left(s"Unmatched path segment: $ss, $ctx"))
          value
        }
      } yield r
    case EndpointInput.PathCapture(m, name, _, _) +: inputsTail =>
      val decodeResult: StateT[Either[Error, ?], Context[F], DecodeResult[Any]] = StateT(
        (ctx: Context[F]) =>
          nextSegment(ctx.unmatchedPath)
            .map {
              case (segment, remaining) =>
                logger.debug(s"Capturing path: $segment, remaining: $remaining, $name")
                (ctx.copy(unmatchedPath = remaining), m.decode(segment))
          })

      decodeResult.flatMap {
        case DecodeResult.Value(v) =>
          logger.debug(s"Decoded path: $v")
          matchInputs[F](inputsTail).map(_.prependValue(v))
        case decodingFailure => StateT.liftF(Either.left(s"Decoding path failed: $decodingFailure"))
      }
    case EndpointInput.Query(name, m, _, _) +: inputsTail =>
      for {
        ctx <- getState[F]
        query = m.decodeOptional(ctx.getQueryParam(name))
        _ = logger.debug(s"Found query: $query, $name, ${ctx.headers}")
        res <- continueMatch(query, inputsTail)
      } yield res
    case EndpointIO.Header(name, m, _, _) +: inputsTail =>
      for {
        ctx <- getState[F]
        header = m.decodeOptional(ctx.getHeader(name))
        _ = logger.debug(s"Found header: $header")
        res <- continueMatch(header, inputsTail)
      } yield res
    case EndpointIO.Body(codec, _, _) +: inputsTail =>
      for {
        ctx <- getState[F]
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

  private def continueMatch[F[_]: Sync](decodeResult: DecodeResult[Any], inputsTail: Vector[EndpointInput.Single[_]]): ContextState[F] =
    decodeResult match {
      case DecodeResult.Value(v) =>
        logger.debug(s"Continuing match: $v")
        matchInputs[F](inputsTail).map(_.prependValue(v))
      case err =>
        StateT.inspectF((ctx: Context[F]) => Either.left(s"${err.toString}, ${ctx.unmatchedPath}"))
    }

  private def handleMapped[II, T, F[_]: Sync](wrapped: EndpointInput[II],
                                              f: II => T,
                                              inputsTail: Vector[EndpointInput.Single[_]]): ContextState[F] =
    for {
      r1 <- matchInputs[F](wrapped.asVectorOfSingle)
      r2 <- matchInputs[F](inputsTail)
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

  private def getState[F[_]]: StateT[Either[Error, ?], Context[F], Context[F]] = StateT.get
  private def modifyState[F[_]: Applicative](f: Context[F] => Context[F]): StateT[Either[Error, ?], Context[F], Unit] =
    StateT.modify[Either[Error, ?], Context[F]](f)
}
