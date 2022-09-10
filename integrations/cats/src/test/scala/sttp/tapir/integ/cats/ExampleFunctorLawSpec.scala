package sttp.tapir.integ.cats

import cats.Eq
import cats.laws.discipline.FunctorTests
import org.scalacheck.Arbitrary
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.Checkers
import org.typelevel.discipline.scalatest.FunSuiteDiscipline
import sttp.tapir.EndpointIO.Example
import sttp.tapir.integ.cats.instances._

class ExampleFunctorLawSpec extends AnyFunSuite with FunSuiteDiscipline with Checkers {
  implicit def exampleEq[A]: Eq[Example[A]] = Eq.fromUniversalEquals

  implicit def exampleArbitrary[T: Arbitrary]: Arbitrary[Example[T]] = Arbitrary {
    for {
      t <- Arbitrary.arbitrary[T]
      name <- Arbitrary.arbitrary[Option[String]]
      summary <- Arbitrary.arbitrary[Option[String]]
      description <- Arbitrary.arbitrary[Option[String]]
    } yield Example(t, name, summary, description)
  }

  checkAll("Example.FunctorLaws", FunctorTests[Example].functor[Int, Int, String])
}
