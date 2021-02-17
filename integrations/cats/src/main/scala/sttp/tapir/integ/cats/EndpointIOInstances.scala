package sttp.tapir.integ.cats

import cats.{InvariantMonoidal, InvariantSemigroupal}
import sttp.tapir.{EndpointInput, EndpointOutput, EndpointIO, emptyOutput}

trait EndpointIOInstances {
  implicit val endpointIOInvariantSemigroupal: InvariantSemigroupal[EndpointIO] =
    new InvariantSemigroupal[EndpointIO] {
      override def imap[A, B](fa: EndpointIO[A])(f: A => B)(g: B => A): EndpointIO[B] =
        fa.map(f)(g)

      override def product[A, B](fa: EndpointIO[A], fb: EndpointIO[B]): EndpointIO[(A, B)] =
        fa.and(fb)
    }

  implicit val endpointInputInvariantSemigroupal: InvariantSemigroupal[EndpointInput] =
    new InvariantSemigroupal[EndpointInput] {
      override def imap[A, B](fa: EndpointInput[A])(f: A => B)(g: B => A): EndpointInput[B] =
        fa.map(f)(g)

      override def product[A, B](fa: EndpointInput[A], fb: EndpointInput[B]): EndpointInput[(A, B)] =
        fa.and(fb)
    }

  implicit val endpointOutputInvariantMonoidal: InvariantMonoidal[EndpointOutput] =
    new InvariantMonoidal[EndpointOutput] {
      override def imap[A, B](fa: EndpointOutput[A])(f: A => B)(g: B => A): EndpointOutput[B] =
        fa.map(f)(g)

      override def product[A, B](fa: EndpointOutput[A], fb: EndpointOutput[B]): EndpointOutput[(A, B)] =
        fa.and(fb)

      override def unit: EndpointOutput[Unit] = emptyOutput
    }
}
