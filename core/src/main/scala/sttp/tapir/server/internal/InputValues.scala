package sttp.tapir.server.internal

import sttp.tapir.internal.{MkParams, SeqToParams}
import sttp.tapir.{DecodeResult, EndpointIO, EndpointInput, Mapping}

sealed trait InputValuesResult
object InputValuesResult {
  sealed trait HasValue extends InputValuesResult {
    def value: Any
  }
  case class Value(value: Any, remainingBasicValues: Vector[Any]) extends HasValue
  case class NoValue(remainingBasicValues: Vector[Any]) extends HasValue {
    override def value: Any = ()
  }
  case class Failure(input: EndpointInput[_], failure: DecodeResult.Failure) extends InputValuesResult
}

object InputValues {

  /**
    * Returns the value of the input, tupled and mapped as described by the data structure. Values of basic inputs
    * are taken as consecutive values from `values.basicInputsValues`. Hence, these should match (in order).
    */
  def apply(input: EndpointInput[_], values: DecodeInputsResult.Values): InputValuesResult =
    apply(input, values.basicInputsValues)

  private def apply(input: EndpointInput[_], remainingBasicValues: Vector[Any]): InputValuesResult = {
    input match {
      case EndpointInput.Multiple(inputs, mkParams, _)  => handleMultiple(inputs, mkParams, Vector(), remainingBasicValues)
      case EndpointIO.Multiple(inputs, mkParams, _)     => handleMultiple(inputs, mkParams, Vector(), remainingBasicValues)
      case EndpointInput.MappedMultiple(wrapped, codec) => handleMappedTuple(wrapped, codec, remainingBasicValues)
      case EndpointIO.MappedMultiple(wrapped, codec)    => handleMappedTuple(wrapped, codec, remainingBasicValues)
      case auth: EndpointInput.Auth[_]                  => apply(auth.input, remainingBasicValues)
      case _: EndpointInput.Basic[_] =>
        remainingBasicValues match {
          case () +: valuesTail => InputValuesResult.NoValue(valuesTail)
          case v +: valuesTail  => InputValuesResult.Value(v, valuesTail)
          case Vector() =>
            throw new IllegalStateException(s"Mismatch between basic input values: $remainingBasicValues, and basic inputs in: $input")
        }
    }
  }

  @scala.annotation.tailrec
  private def handleMultiple(
      inputs: Vector[EndpointInput[_]],
      mkParams: MkParams,
      acc: Vector[Any],
      remainingBasicValues: Vector[Any]
  ): InputValuesResult = {
    inputs match {
      case Vector() =>
        if (acc.isEmpty) InputValuesResult.NoValue(remainingBasicValues)
        else InputValuesResult.Value(mkParams(acc), remainingBasicValues)
      case input +: inputsTail =>
        apply(input, remainingBasicValues) match {
          case InputValuesResult.Value(inputValue, remainingBasicValues2) =>
            handleMultiple(inputsTail, mkParams, acc :+ inputValue, remainingBasicValues2)
          case InputValuesResult.NoValue(remainingBasicValues2) => handleMultiple(inputsTail, mkParams, acc, remainingBasicValues2)
          case f: InputValuesResult.Failure                     => f
        }
    }
  }

  private def handleMappedTuple[II, T](
      wrapped: EndpointInput[II],
      codec: Mapping[II, T],
      remainingBasicValues: Vector[Any]
  ): InputValuesResult = {
    apply(wrapped, remainingBasicValues) match {
      case InputValuesResult.Value(tupleValue, remainingBasicValues2) =>
        codec.decode(tupleValue.asInstanceOf[II]) match {
          case DecodeResult.Value(v)   => InputValuesResult.Value(v, remainingBasicValues2)
          case f: DecodeResult.Failure => InputValuesResult.Failure(wrapped, f)
        }
      case InputValuesResult.NoValue(remainingBasicValues2) => InputValuesResult.NoValue(remainingBasicValues2)
      case InputValuesResult.Failure(input, failure)        => InputValuesResult.Failure(input, failure)
    }
  }
}
