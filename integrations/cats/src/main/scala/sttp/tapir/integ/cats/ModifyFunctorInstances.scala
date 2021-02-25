package sttp.tapir.integ.cats

import cats.data.{Chain, NonEmptyChain, NonEmptyList, NonEmptySet}
import sttp.tapir._

trait ModifyFunctorInstances {

  implicit def nonEmptyListModifyFuntor[A]: ModifyFunctor[NonEmptyList, A] =
    new ModifyFunctor[NonEmptyList, A] {}

  implicit def nonEmptySetModifyFunctor[A]: ModifyFunctor[NonEmptySet, A] =
    new ModifyFunctor[NonEmptySet, A] {}

  implicit def chainModifyFunctor[A]: ModifyFunctor[Chain, A] =
    new ModifyFunctor[Chain, A] {}

  implicit def nonEmptyChainModifyFunctor[A]: ModifyFunctor[NonEmptyChain, A] =
    new ModifyFunctor[NonEmptyChain, A] {}
}
