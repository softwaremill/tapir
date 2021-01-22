package sttp.tapir.server.internal

import sttp.capabilities.Effect
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.internal.{CombineParams, Params, ParamsAsAny, RichVector}
import sttp.tapir.{DecodeResult, EndpointIO, EndpointInput, Mapping}

sealed trait InputValuesResult
object InputValuesResult {
  case class Value(params: Params, remainingBasicValues: Vector[Any]) extends InputValuesResult
  case class Failure(input: EndpointInput[_, _], failure: DecodeResult.Failure) extends InputValuesResult
}

class InputValues[F[_]](implicit monad: MonadError[F]) {
  private def apply[R](input: EndpointInput[_, R with Effect[F]], remainingBasicValues: Vector[Any]): F[InputValuesResult] = {
    input match {
      case EndpointInput.Pair(left, right, combine, _)       => handlePair(left, right, combine, remainingBasicValues)
      case EndpointIO.Pair(left, right, combine, _)          => handlePair(left, right, combine, remainingBasicValues)
      case EndpointInput.MapEffect(input, f, _)              => handleMapEffect(input, f(monad), remainingBasicValues)
      case EndpointIO.MapEffect(input, f, _)                 => handleMapEffect(input, f(monad), remainingBasicValues)
      case EndpointInput.MappedPair(wrapped, codec)          => handleMappedPair(wrapped, codec, remainingBasicValues)
      case EndpointIO.MappedPair(wrapped, codec)             => handleMappedPair(wrapped, codec, remainingBasicValues)
      case auth: EndpointInput.Auth[_, Effect[F] @unchecked] => apply(auth.input, remainingBasicValues)
      case _: EndpointInput.Basic[_, _] =>
        remainingBasicValues.headAndTail match {
          case Some((v, valuesTail)) => (InputValuesResult.Value(ParamsAsAny(v), valuesTail): InputValuesResult).unit
          case None =>
            throw new IllegalStateException(s"Mismatch between basic input values: $remainingBasicValues, and basic inputs in: $input")
        }
    }
  }

  private def handleMapEffect[T, U, R](
      input: EndpointInput[T, R with Effect[F]],
      f: T => F[DecodeResult[U]],
      remainingBasicValues: Vector[Any]
  ): F[InputValuesResult] = {
    apply(input, remainingBasicValues).flatMap {
      case InputValuesResult.Value(params, remainingBasicValues2) =>
        f(params.asAny.asInstanceOf[T])
          .map {
            case DecodeResult.Value(v)   => InputValuesResult.Value(ParamsAsAny(v), remainingBasicValues2)
            case f: DecodeResult.Failure => InputValuesResult.Failure(input, f): InputValuesResult
          }
          .handleError { case e: Exception =>
            (InputValuesResult.Failure(input, DecodeResult.Error(params.asAny.toString, e)): InputValuesResult).unit
          }
      case f: InputValuesResult.Failure => (f: InputValuesResult).unit
    }
  }

  private def handlePair[R](
      left: EndpointInput[_, R with Effect[F]],
      right: EndpointInput[_, R with Effect[F]],
      combine: CombineParams,
      remainingBasicValues: Vector[Any]
  ): F[InputValuesResult] = {
    apply(left, remainingBasicValues).flatMap {
      case InputValuesResult.Value(leftParams, remainingBasicValues2) =>
        apply(right, remainingBasicValues2).flatMap {
          case InputValuesResult.Value(rightParams, remainingBasicValues3) =>
            (InputValuesResult.Value(combine(leftParams, rightParams), remainingBasicValues3): InputValuesResult).unit
          case f2: InputValuesResult.Failure => (f2: InputValuesResult).unit
        }
      case f: InputValuesResult.Failure => (f: InputValuesResult).unit
    }
  }

  private def handleMappedPair[II, T, R](
      wrapped: EndpointInput[II, R with Effect[F]],
      codec: Mapping[II, T],
      remainingBasicValues: Vector[Any]
  ): F[InputValuesResult] = {
    apply(wrapped, remainingBasicValues).map {
      case InputValuesResult.Value(pairValue, remainingBasicValues2) =>
        codec.decode(pairValue.asAny.asInstanceOf[II]) match {
          case DecodeResult.Value(v)   => InputValuesResult.Value(ParamsAsAny(v), remainingBasicValues2)
          case f: DecodeResult.Failure => InputValuesResult.Failure(wrapped, f)
        }
      case f: InputValuesResult.Failure => f
    }
  }
}

object InputValues {

  /** Returns the value of the input, tupled and mapped as described by the data structure. Values of basic inputs
    * are taken as consecutive values from `values.basicInputsValues`. Hence, these should match (in order).
    */
  def apply[R, F[_]](input: EndpointInput[_, R with Effect[F]], values: DecodeInputsResult.Values)(implicit
      monad: MonadError[F]
  ): F[InputValuesResult] =
    new InputValues()(monad).apply(input, values.basicInputsValues)
}
