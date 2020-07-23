package sttp.tapir.integ.cats

import cats.Functor
import sttp.tapir.EndpointIO.Example

trait ExampleInstances {
  implicit val exampleFunctor: Functor[Example] = new Functor[Example] {
    override def map[A, B](example: Example[A])(f: A => B): Example[B] = example.map(f)
  }
}
