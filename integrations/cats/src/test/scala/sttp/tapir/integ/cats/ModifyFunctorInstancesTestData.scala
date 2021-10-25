package sttp.tapir.integ.cats

import cats.data.{Chain, NonEmptyChain, NonEmptyList, NonEmptySet}

// This needs to be in a separate file than ModifyFunctorInstancesTest as otherwise compiling for Scala 3 using Scala.JS
// gives:
// [error]    |Cannot call macro class NonEmptyListWrapper defined in the same source file
// [error]    | This location contains code that was inlined from ModifyFunctorInstancesTest.scala:16

case class NonEmptyListWrapper(f1: NonEmptyList[String])
case class NonEmptySetWrapper(f1: NonEmptySet[String])
case class ChainWrapper(f1: Chain[String])
case class NonEmptyChainWrapper(f1: NonEmptyChain[String])
