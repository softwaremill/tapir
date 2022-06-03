package sttp.tapir.server.interpreter

import sttp.tapir.internal.{CombineParams, Params, ParamsAsAny, RichVector}
import sttp.tapir.{DecodeResult, EndpointIO, EndpointInput, Mapping}

sealed trait InputValueResult
object InputValueResult {
  case class Value(params: Params, remainingBasicValues: Vector[Any]) extends InputValueResult
  case class Failure(input: EndpointInput[_], failure: DecodeResult.Failure) extends InputValueResult
}

object InputValue {

  /** Returns the value of the input, tupled and mapped as described by the data structure. Values of basic inputs are taken as consecutive
    * values from `values.basicInputsValues`. Hence, these should match (in order).
    */
  def apply(input: EndpointInput[_], values: DecodeBasicInputsResult.Values): InputValueResult =
    apply(input, values.basicInputsValues)

  private def apply(input: EndpointInput[_], remainingBasicValues: Vector[Any]): InputValueResult = {
    input match {
      case EndpointInput.Pair(left, right, combine, _) => handlePair(left, right, combine, remainingBasicValues)
      case EndpointIO.Pair(left, right, combine, _)    => handlePair(left, right, combine, remainingBasicValues)
      case EndpointInput.MappedPair(wrapped, codec)    => handleMappedPair(wrapped, codec, remainingBasicValues)
      case EndpointIO.MappedPair(wrapped, codec)       => handleMappedPair(wrapped, codec, remainingBasicValues)
      case auth: EndpointInput.Auth[_, _]              => apply(auth.input, remainingBasicValues)
      case _: EndpointInput.Basic[_] =>
        remainingBasicValues.headAndTail match {
          case Some((v, valuesTail)) => InputValueResult.Value(ParamsAsAny(v), valuesTail)
          case None =>
            throw new IllegalStateException(s"Mismatch between basic input values: $remainingBasicValues, and basic inputs in: $input")
        }
    }
  }

  private def handlePair(
      left: EndpointInput[_],
      right: EndpointInput[_],
      combine: CombineParams,
      remainingBasicValues: Vector[Any]
  ): InputValueResult = {
    apply(left, remainingBasicValues) match {
      case InputValueResult.Value(leftParams, remainingBasicValues2) =>
        apply(right, remainingBasicValues2) match {
          case InputValueResult.Value(rightParams, remainingBasicValues3) =>
            InputValueResult.Value(combine(leftParams, rightParams), remainingBasicValues3)
          case f2: InputValueResult.Failure => f2
        }
      case f: InputValueResult.Failure => f
    }
  }

  private def handleMappedPair[II, T](
      wrapped: EndpointInput[II],
      codec: Mapping[II, T],
      remainingBasicValues: Vector[Any]
  ): InputValueResult = {
    apply(wrapped, remainingBasicValues) match {
      case InputValueResult.Value(pairValue, remainingBasicValues2) =>
        codec.decode(pairValue.asAny.asInstanceOf[II]) match {
          case DecodeResult.Value(v)   => InputValueResult.Value(ParamsAsAny(v), remainingBasicValues2)
          case f: DecodeResult.Failure => InputValueResult.Failure(wrapped, f)
        }
      case f: InputValueResult.Failure => f
    }
  }
}
