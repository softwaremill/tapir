package sttp.tapir.tests

import cats.effect.{ContextShift, IO, Resource}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

trait TestSuite extends AnyFunSuite with BeforeAndAfterAll {

  implicit lazy val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)

  def tests: Resource[IO, List[Test[_]]]
  def testNameFilter: Option[String] = None // define to run a single test (temporarily for debugging)

  // we need to register the tests when the class is constructed, as otherwise scalatest skips it
  val (allTests, doRelease) = tests.allocated.unsafeRunSync()
  allTests.foreach { t =>
    if (testNameFilter.forall(filter => t.name.contains(filter))) {
      test(t.name)(t.f())(t.pos)
    }
  }
  private val release = doRelease

  override protected def afterAll(): Unit = {
    // the resources can only be released after all of the tests are run
    release.unsafeRunSync()
    super.afterAll()
  }
}
