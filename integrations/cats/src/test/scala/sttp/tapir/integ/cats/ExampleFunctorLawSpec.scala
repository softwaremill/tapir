package sttp.tapir.integ.cats

import cats.Eq
import cats.implicits._
import cats.laws.discipline.FunctorTests
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.Checkers
import org.typelevel.discipline.scalatest.FunSuiteDiscipline
import sttp.tapir.EndpointIO.Example
import sttp.tapir.integ.cats.instances._
import org.scalacheck.ScalacheckShapeless._

class ExampleFunctorLawSpec  extends AnyFunSuite with FunSuiteDiscipline with Checkers {
  implicit def eqTree[A: Eq]: Eq[Example[A]] = Eq.fromUniversalEquals

  checkAll("Example.FunctorLaws", FunctorTests[Example].functor[Int, Int, String])
}

