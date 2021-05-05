package sttp.tapir.tests

import cats.effect.std.Dispatcher
import cats.effect.{IO, Resource}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import cats.effect.unsafe.implicits.global

trait TestSuite extends AnyFunSuite with BeforeAndAfterAll {
  def tests: Resource[IO, List[Test]]
  def testNameFilter: Option[String] = None // define to run a single test (temporarily for debugging)

  protected val (dispatcher, shutdownDispatcher) = Dispatcher[IO].allocated.unsafeRunSync()

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
    shutdownDispatcher.unsafeRunSync()
    super.afterAll()
  }
}
