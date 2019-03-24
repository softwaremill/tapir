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
    // the first decoding failure is returned. We decode in the following order: method, path, query, headers (incl. cookies), request, status, body
    val inputs = input.asVectorOfBasicInputs().sortBy {
      case _: EndpointInput.RequestMethod         => 0
      case _: EndpointInput.PathSegment           => 1
      case _: EndpointInput.PathCapture[_]        => 1
      case _: EndpointInput.PathsCapture          => 1
      case _: EndpointInput.Query[_]              => 2
      case _: EndpointInput.QueryParams           => 2
      case _: EndpointInput.Cookie[_]             => 3
      case _: EndpointIO.Header[_]                => 3
      case _: EndpointIO.Headers                  => 3
      case _: EndpointInput.ExtractFromRequest[_] => 4
      case _: EndpointIO.Body[_, _, _]            => 6
      case _: EndpointIO.StreamBodyWrapper[_, _]  => 6
    }

    val (result, consumedCtx) = apply(inputs, DecodeInputsResult.Values(Map(), None), ctx)

    result match {
      case v: DecodeInputsResult.Values => verifyPathExactMatch(inputs, consumedCtx).getOrElse(v)
      case r                            => r
    }
  }

  private def apply(inputs: Vector[EndpointInput.Basic[_]],
                    values: DecodeInputsResult.Values,
                    ctx: DecodeInputsContext): (DecodeInputsResult, DecodeInputsContext) = {
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
            codec.decode(s) match {
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
        codec.decode(ctx.queryParameter(name).toList) match {
          case DecodeResult.Value(v)  => apply(inputsTail, values.value(input, v), ctx)
          case failure: DecodeFailure => (DecodeInputsResult.Failure(input, failure), ctx)
        }

      case (input @ EndpointInput.QueryParams(_)) +: inputsTail =>
        apply(inputsTail, values.value(input, MultiQueryParams.fromMultiMap(ctx.queryParameters)), ctx)

      case (input @ EndpointInput.Cookie(name, codec, _)) +: inputsTail =>
        val allCookies = DecodeResult.sequence(ctx.headers.filter(_._1 == Cookie.HeaderName).map(p => Cookie.parse(p._2)).toList)
        val cookieValue = allCookies.map(_.flatten.find(_.name == name)).flatMap(cookie => codec.decode(cookie.map(_.value)))
        cookieValue match {
          case DecodeResult.Value(v)  => apply(inputsTail, values.value(input, v), ctx)
          case failure: DecodeFailure => (DecodeInputsResult.Failure(input, failure), ctx)
        }

      case (input @ EndpointIO.Header(name, codec, _)) +: inputsTail =>
        codec.decode(ctx.header(name)) match {
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
  private def verifyPathExactMatch(inputs: Vector[EndpointInput.Basic[_]], ctx: DecodeInputsContext): Option[DecodeInputsResult.Failure] = {
    inputs.filter {
      case _: EndpointInput.PathSegment    => true
      case _: EndpointInput.PathCapture[_] => true
      case _: EndpointInput.PathsCapture   => true
      case _                               => false
    }.lastOption match {
      case Some(lastPathInput) =>
        ctx.nextPathSegment._1 match {
          case Some(nextPathSegment) =>
            Some(DecodeInputsResult.Failure(lastPathInput, DecodeResult.Mismatch("", nextPathSegment)))
          case None => None
        }

      case None => None
    }
  }
}
