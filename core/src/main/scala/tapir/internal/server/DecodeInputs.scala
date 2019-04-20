package tapir.internal.server

import tapir.model.{Cookie, Method, MultiQueryParams, ServerRequest}
import tapir.{DecodeFailure, DecodeResult, EndpointIO, EndpointInput}
import tapir.internal._

import scala.annotation.tailrec

trait DecodeInputsResult
object DecodeInputsResult {
  case class Values(values: Map[EndpointInput.Basic[_], Any], bodyInput: Option[EndpointIO.Body[_, _, _]]) extends DecodeInputsResult {
    def value(i: EndpointInput.Basic[_], v: Any): Values = copy(values = values + (i -> v))
  }
  case class Failure(input: EndpointInput.Basic[_], failure: DecodeFailure) extends DecodeInputsResult
}

trait DecodeInputsContext {
  def method: Method

  def nextPathSegment: (Option[String], DecodeInputsContext)

  def header(name: String): List[String]
  def headers: Seq[(String, String)]

  def queryParameter(name: String): Seq[String]
  def queryParameters: Map[String, Seq[String]]

  def bodyStream: Any

  def serverRequest: ServerRequest
}

object DecodeInputs {

  /**
    * Decodes values of all inputs defined by the given `input`, and returns a map from the input to the input's value.
    *
    * An exception is the body input, which is not decoded. This is because typically bodies can be only read once.
    * That's why, all non-body inputs are used to decide if a request matches the endpoint, or not. If a body input
    * is present, it is also returned as part of the result.
    *
    * In case any of the decoding fails, the failure is returned together with the failing input.
    */
  def apply(input: EndpointInput[_], ctx: DecodeInputsContext): DecodeInputsResult = {
    // The first decoding failure is returned.
    // We decode in the following order: method, path, query, headers (incl. cookies), request, status, body
    // An exact-path check is done after method & path matching

    val basicInputs = input.asVectorOfBasicInputs()

    val methodInputs = basicInputs.filter(isRequestMethod)
    val pathInputs = basicInputs.filter(isPath)
    val otherInputs = basicInputs.filterNot(ei => isRequestMethod(ei) || isPath(ei)).sortBy {
      case _: EndpointInput.Query[_]              => 2
      case _: EndpointInput.QueryParams           => 2
      case _: EndpointInput.Cookie[_]             => 3
      case _: EndpointIO.Header[_]                => 3
      case _: EndpointIO.Headers                  => 3
      case _: EndpointInput.ExtractFromRequest[_] => 4
      case _: EndpointIO.Body[_, _, _]            => 6
      case _: EndpointIO.StreamBodyWrapper[_, _]  => 6
      // filtered out earlier
      case _: EndpointInput.RequestMethod  => throw new IllegalArgumentException
      case _: EndpointInput.PathSegment    => throw new IllegalArgumentException
      case _: EndpointInput.PathCapture[_] => throw new IllegalArgumentException
      case _: EndpointInput.PathsCapture   => throw new IllegalArgumentException
    }

    compose(
      apply(methodInputs, _, _),
      apply(pathInputs, _, _),
      (values, ctx) =>
        verifyPathExactMatch(pathInputs, ctx) match {
          case None          => (values, ctx)
          case Some(failure) => (failure, ctx)
        },
      apply(otherInputs, _, _)
    )(DecodeInputsResult.Values(Map(), None), ctx)._1
  }

  private def apply(
      inputs: Vector[EndpointInput.Basic[_]],
      values: DecodeInputsResult.Values,
      ctx: DecodeInputsContext
  ): (DecodeInputsResult, DecodeInputsContext) = {
    inputs match {
      case Vector() => (values, ctx)

      case (input @ EndpointInput.RequestMethod(m)) +: inputsTail =>
        if (m == ctx.method) apply(inputsTail, values, ctx)
        else (DecodeInputsResult.Failure(input, DecodeResult.Mismatch(m.m, ctx.method.m)), ctx)

      case (input @ EndpointInput.PathSegment(ss)) +: inputsTail =>
        ctx.nextPathSegment match {
          case (Some(`ss`), ctx2)       => apply(inputsTail, values, ctx2)
          case (None, ctx2) if ss == "" => apply(inputsTail, values, ctx2) // root path
          case (Some(s), _)             => (DecodeInputsResult.Failure(input, DecodeResult.Mismatch(ss, s)), ctx)
          case (None, _)                => (DecodeInputsResult.Failure(input, DecodeResult.Missing), ctx)
        }

      case (input @ EndpointInput.PathCapture(codec, _, _)) +: inputsTail =>
        ctx.nextPathSegment match {
          case (Some(s), ctx2) =>
            codec.safeDecode(s) match {
              case DecodeResult.Value(v)  => apply(inputsTail, values.value(input, v), ctx2)
              case failure: DecodeFailure => (DecodeInputsResult.Failure(input, failure), ctx)
            }
          case (None, _) => (DecodeInputsResult.Failure(input, DecodeResult.Missing), ctx)
        }

      case (input @ EndpointInput.PathsCapture(_)) +: inputsTail =>
        @tailrec
        def remainingPath(acc: Vector[String], c: DecodeInputsContext): (Vector[String], DecodeInputsContext) = c.nextPathSegment match {
          case (Some(s), c2) => remainingPath(acc :+ s, c2)
          case (None, c2)    => (acc, c2)
        }

        val (ps, ctx2) = remainingPath(Vector.empty, ctx)

        apply(inputsTail, values.value(input, ps), ctx2)

      case (input @ EndpointInput.Query(name, codec, _)) +: inputsTail =>
        codec.safeDecode(ctx.queryParameter(name).toList) match {
          case DecodeResult.Value(v)  => apply(inputsTail, values.value(input, v), ctx)
          case failure: DecodeFailure => (DecodeInputsResult.Failure(input, failure), ctx)
        }

      case (input @ EndpointInput.QueryParams(_)) +: inputsTail =>
        apply(inputsTail, values.value(input, MultiQueryParams.fromMultiMap(ctx.queryParameters)), ctx)

      case (input @ EndpointInput.Cookie(name, codec, _)) +: inputsTail =>
        val allCookies = DecodeResult.sequence(ctx.headers.filter(_._1 == Cookie.HeaderName).map(p => Cookie.parse(p._2)).toList)
        val cookieValue =
          allCookies.map(_.flatten.find(_.name == name)).flatMap(cookie => codec.safeDecode(cookie.map(_.value)))
        cookieValue match {
          case DecodeResult.Value(v)  => apply(inputsTail, values.value(input, v), ctx)
          case failure: DecodeFailure => (DecodeInputsResult.Failure(input, failure), ctx)
        }

      case (input @ EndpointIO.Header(name, codec, _)) +: inputsTail =>
        codec.safeDecode(ctx.header(name)) match {
          case DecodeResult.Value(v)  => apply(inputsTail, values.value(input, v), ctx)
          case failure: DecodeFailure => (DecodeInputsResult.Failure(input, failure), ctx)
        }

      case (input @ EndpointIO.Headers(_)) +: inputsTail =>
        apply(inputsTail, values.value(input, ctx.headers), ctx)

      case (input @ EndpointInput.ExtractFromRequest(f)) +: inputsTail =>
        apply(inputsTail, values.value(input, f(ctx.serverRequest)), ctx)

      case (input @ EndpointIO.Body(_, _)) +: inputsTail =>
        apply(inputsTail, values.copy(bodyInput = Some(input)), ctx)

      case (input @ EndpointIO.StreamBodyWrapper(_)) +: inputsTail =>
        apply(inputsTail, values.value(input, ctx.bodyStream), ctx)
    }
  }

  /**
    * If there's any path input, the path must match exactly.
    */
  private def verifyPathExactMatch(
      pathInputs: Vector[EndpointInput.Basic[_]],
      ctx: DecodeInputsContext
  ): Option[DecodeInputsResult.Failure] = {
    pathInputs.lastOption match {
      case Some(lastPathInput) =>
        ctx.nextPathSegment._1 match {
          case Some(nextPathSegment) =>
            Some(DecodeInputsResult.Failure(lastPathInput, DecodeResult.Mismatch("", nextPathSegment)))
          case None => None
        }

      case None => None
    }
  }

  private val isRequestMethod: EndpointInput.Basic[_] => Boolean = {
    case _: EndpointInput.RequestMethod => true
    case _                              => false
  }

  private val isPath: EndpointInput.Basic[_] => Boolean = {
    case _: EndpointInput.PathSegment    => true
    case _: EndpointInput.PathCapture[_] => true
    case _: EndpointInput.PathsCapture   => true
    case _                               => false
  }

  private type DecodeInputResultTransform = (DecodeInputsResult.Values, DecodeInputsContext) => (DecodeInputsResult, DecodeInputsContext)
  private def compose(fs: DecodeInputResultTransform*): DecodeInputResultTransform = { (values, ctx) =>
    fs match {
      case f +: tail =>
        f(values, ctx) match {
          case (values2: DecodeInputsResult.Values, ctx2) => compose(tail: _*)(values2, ctx2)
          case r                                          => r
        }
      case _ => (values, ctx)
    }
  }
}
