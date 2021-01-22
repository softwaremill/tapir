package sttp.tapir.internal

import sttp.capabilities.Effect
import sttp.monad.MonadError
import sttp.tapir.EndpointOutput.StatusMapping
import sttp.tapir.{DecodeResult, Endpoint, EndpointIO, EndpointInput, EndpointOutput}

object ChangeEffect {
  def apply[F[_], G[_], I, E, O, R0](
      e: Endpoint[I, E, O, R0 with Effect[F]],
      fk: FunctionK[F, G],
      fm: MonadError[F]
  ): Endpoint[I, E, O, R0 with Effect[G]] = {

    def mapInputEffect[T, U](
        input: EndpointInput[T, R0 with Effect[F]],
        f: MonadError[F] => T => F[DecodeResult[U]],
        g: MonadError[F] => U => F[T]
    ): EndpointInput[U, R0 with Effect[G]] =
      EndpointInput.MapEffect(mapInput(input), _ => (t: T) => fk(f(fm)(t)), _ => u => fk(g(fm)(u)))

    def mapInput[T](ei: EndpointInput[T, R0 with Effect[F]]): EndpointInput[T, R0 with Effect[G]] = ei match {
      case EndpointInput.MapEffect(input, f, g) => mapInputEffect(input, f, g)
      case EndpointIO.MapEffect(input, f, g)    => mapInputEffect(input, f, g)
      case EndpointInput.MappedPair(EndpointInput.Pair(left, right, combine, split), mapping) =>
        EndpointInput
          .MappedPair(EndpointInput.Pair[Any, Any, Any, R0 with Effect[G]](mapInput(left), mapInput(right), combine, split), mapping)
      case EndpointIO.MappedPair(EndpointIO.Pair(left, right, combine, split), mapping) =>
        EndpointInput
          .MappedPair(EndpointInput.Pair[Any, Any, Any, R0 with Effect[G]](mapInput(left), mapInput(right), combine, split), mapping)
          .asInstanceOf[EndpointInput[T, R0 with Effect[G]]]
      case EndpointInput.Pair(left, right, combine, split) => EndpointInput.Pair(mapInput(left), mapInput(right), combine, split)
      case EndpointIO.Pair(left, right, combine, split)    => EndpointInput.Pair(mapInput(left), mapInput(right), combine, split)
      case EndpointInput.Auth.ApiKey(input, challenge, ssn) =>
        EndpointInput.Auth.ApiKey(mapInput(input).asInstanceOf[EndpointInput.Single[T, R0 with Effect[G]]], challenge, ssn)
      case EndpointInput.Auth.Http(scheme, input, challenge, ssn) =>
        EndpointInput.Auth.Http(scheme, mapInput(input).asInstanceOf[EndpointInput.Single[T, R0 with Effect[G]]], challenge, ssn)
      case EndpointInput.Auth.Oauth2(aurl, turl, scopes, rurl, input, challenge, ssn) =>
        EndpointInput.Auth.Oauth2(
          aurl,
          turl,
          scopes,
          rurl,
          mapInput(input).asInstanceOf[EndpointInput.Single[T, R0 with Effect[G]]],
          challenge,
          ssn
        )
      case EndpointInput.Auth.ScopedOauth2(EndpointInput.Auth.Oauth2(aurl, turl, scopes, rurl, input, challenge, ssn), rs) =>
        EndpointInput.Auth.ScopedOauth2(
          EndpointInput.Auth
            .Oauth2(aurl, turl, scopes, rurl, mapInput(input).asInstanceOf[EndpointInput.Single[T, R0 with Effect[G]]], challenge, ssn),
          rs
        )
      case _ => ei.asInstanceOf[EndpointInput[T, R0 with Effect[G]]]
    }

    def mapOutputEffect[T, U](
        output: EndpointOutput[T, R0 with Effect[F]],
        f: MonadError[F] => T => F[DecodeResult[U]],
        g: MonadError[F] => U => F[T]
    ): EndpointOutput[U, R0 with Effect[G]] =
      EndpointOutput.MapEffect(mapOutput(output), _ => (t: T) => fk(f(fm)(t)), _ => u => fk(g(fm)(u)))

    def mapOutput[T](eo: EndpointOutput[T, R0 with Effect[F]]): EndpointOutput[T, R0 with Effect[G]] = eo match {
      case EndpointOutput.MapEffect(output, f, g) => mapOutputEffect(output, f, g)
      case EndpointIO.MapEffect(output, f, g)     => mapOutputEffect(output, f, g)
      case EndpointOutput.MappedPair(EndpointOutput.Pair(l, r, combine, split), mapping) =>
        EndpointOutput.MappedPair(
          EndpointOutput.Pair[Any, Any, Any, R0 with Effect[G]](mapOutput(l), mapOutput(r), combine, split),
          mapping
        )
      case EndpointIO.MappedPair(EndpointIO.Pair(left, right, combine, split), mapping) =>
        EndpointOutput.MappedPair(
          EndpointOutput.Pair[Any, Any, Any, R0 with Effect[G]](mapOutput(left), mapOutput(right), combine, split),
          mapping
        )
      case EndpointOutput.Pair(l, r, combine, split)    => EndpointOutput.Pair(mapOutput(l), mapOutput(r), combine, split)
      case EndpointIO.Pair(left, right, combine, split) => EndpointOutput.Pair(mapOutput(left), mapOutput(right), combine, split)
      case EndpointOutput.OneOf(mappings, codec) =>
        EndpointOutput.OneOf[Any, T, R0 with Effect[G]](
          mappings.map { case StatusMapping(statusCode, output, appliesTo) =>
            StatusMapping(statusCode, mapOutput(output), appliesTo)
          },
          codec
        )
      case _ => eo.asInstanceOf[EndpointOutput[T, R0 with Effect[G]]]
    }

    def inputHasEffect(ei: EndpointInput[_, _]): Boolean = ei.traverseInputs {
      case EndpointInput.MapEffect(_, _, _) => Vector(())
      case EndpointIO.MapEffect(_, _, _)    => Vector(())
    }.nonEmpty

    def outputHasEffect(eo: EndpointOutput[_, _]): Boolean = eo.traverseOutputs {
      case EndpointOutput.MapEffect(_, _, _) => Vector(())
      case EndpointIO.MapEffect(_, _, _)     => Vector(())
    }.nonEmpty

    val i = inputHasEffect(e.input)
    val eo = outputHasEffect(e.errorOutput)
    val o = outputHasEffect(e.output)

    if (i || eo || o) {
      Endpoint(
        if (i) mapInput(e.input) else e.input.asInstanceOf[EndpointInput[I, R0 with Effect[G]]],
        if (eo) mapOutput(e.errorOutput) else e.errorOutput.asInstanceOf[EndpointOutput[E, R0 with Effect[G]]],
        if (o) mapOutput(e.output) else e.output.asInstanceOf[EndpointOutput[O, R0 with Effect[G]]],
        e.info
      )
    } else e.asInstanceOf[Endpoint[I, E, O, R0 with Effect[G]]]
  }
}

trait FunctionK[F[_], G[_]] {
  def apply[A](fa: F[A]): G[A]
}
