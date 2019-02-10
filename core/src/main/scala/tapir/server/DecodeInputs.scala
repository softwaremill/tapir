package tapir.server

import tapir.{DecodeFailure, DecodeResult, EndpointIO, EndpointInput, MultiQueryParams}

import scala.annotation.tailrec

trait DecodeInputsResult
object DecodeInputsResult {
  case class Values(values: Map[EndpointInput.Single[_], Any], bodyInput: Option[EndpointIO.Body[_, _, _]]) extends DecodeInputsResult {
    def value(i: EndpointInput.Single[_], v: Any): Values = copy(values = values + (i -> v))
  }
  case class Failure(failure: DecodeFailure, input: EndpointInput.Single[_]) extends DecodeInputsResult
}

trait DecodeInputsContext {
  def nextPathSegment: (Option[String], DecodeInputsContext)

  def header(name: String): List[String]
  def headers: Seq[(String, String)]

  def queryParameter(name: String): Seq[String]
  def queryParameters: Map[String, Seq[String]]

  def bodyStream: Any
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
    apply(input.asVectorOfSingle, DecodeInputsResult.Values(Map(), None), ctx)
  }

  private def apply(inputs: Vector[EndpointInput.Single[_]],
                    values: DecodeInputsResult.Values,
                    ctx: DecodeInputsContext): DecodeInputsResult = {
    inputs match {
      case Vector() => values

      case (input @ EndpointInput.PathSegment(ss)) +: inputsTail =>
        ctx.nextPathSegment match {
          case (Some(`ss`), ctx2) => apply(inputsTail, values, ctx2)
          case (Some(s), _)       => DecodeInputsResult.Failure(DecodeResult.Mismatch(ss, s), input)
          case (None, _)          => DecodeInputsResult.Failure(DecodeResult.Missing, input)
        }

      case (input @ EndpointInput.PathCapture(codec, _, _)) +: inputsTail =>
        ctx.nextPathSegment match {
          case (Some(s), ctx2) =>
            codec.decode(s) match {
              case DecodeResult.Value(v)  => apply(inputsTail, values.value(input, v), ctx2)
              case failure: DecodeFailure => DecodeInputsResult.Failure(failure, input)
            }
          case (None, _) => DecodeInputsResult.Failure(DecodeResult.Missing, input)
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
          case failure: DecodeFailure => DecodeInputsResult.Failure(failure, input)
        }

      case (input @ EndpointInput.QueryParams(_)) +: inputsTail =>
        apply(inputsTail, values.value(input, MultiQueryParams.fromMultiMap(ctx.queryParameters)), ctx)

      case (input @ EndpointIO.Header(name, codec, _)) +: inputsTail =>
        codec.decode(ctx.header(name)) match {
          case DecodeResult.Value(v)  => apply(inputsTail, values.value(input, v), ctx)
          case failure: DecodeFailure => DecodeInputsResult.Failure(failure, input)
        }

      case (input @ EndpointIO.Headers(_)) +: inputsTail =>
        apply(inputsTail, values.value(input, ctx.headers), ctx)

      case (input @ EndpointIO.Body(_, _)) +: inputsTail =>
        apply(inputsTail, values.copy(bodyInput = Some(input)), ctx)

      case (input @ EndpointIO.StreamBodyWrapper(_)) +: inputsTail =>
        apply(inputsTail, values.value(input, ctx.bodyStream), ctx)

      case EndpointInput.Mapped(wrapped, _, _, _) +: inputsTail =>
        apply(wrapped.asVectorOfSingle ++ inputsTail, values, ctx)

      case EndpointIO.Mapped(wrapped, _, _, _) +: inputsTail =>
        apply(wrapped.asVectorOfSingle ++ inputsTail, values, ctx)
    }
  }
}
