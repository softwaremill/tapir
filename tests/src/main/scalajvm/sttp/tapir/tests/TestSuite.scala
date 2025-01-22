package sttp.tapir.tests

import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import org.scalactic.source.Position
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AsyncFunSuite

trait TestSuite extends AsyncFunSuite with BeforeAndAfterAll {
  def tests: Resource[IO, List[Test]]
  def testNameFilter: Option[String] = None // define to run a single test (temporarily for debugging)

  protected val (dispatcher, shutdownDispatcher) = Dispatcher.parallel[IO].allocated.unsafeRunSync()

  // we need to register the tests when the class is constructed, as otherwise scalatest skips it
  val (allTests, doRelease) = tests.allocated.unsafeRunSync()

  allTests.foreach { t =>
    if (testNameFilter.forall(filter => t.name.contains(filter))) {
      implicit val pos: Position = t.pos
      test(t.name)(t.f())
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
