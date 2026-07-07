package sttp.tapir.tests

import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import org.scalactic.source.Position
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AsyncFunSuite

import scala.concurrent.duration._

trait TestSuite extends AsyncFunSuite with BeforeAndAfterAll {
  def tests: Resource[IO, List[Test]]
  def testNameFilter: Option[String] = None // define to run a single test (temporarily for debugging)

  protected val (dispatcher, shutdownDispatcher) = Dispatcher.parallel[IO].allocated.unsafeRunSync()

  // we need to register the tests when the class is constructed, as otherwise scalatest skips it
  // bounding acquisition: a backend which fails to start must not hang the non-forked sbt JVM
  val (allTests, doRelease) = tests.allocated.timeout(2.minutes).unsafeRunSync()

  allTests.foreach { t =>
    if (testNameFilter.forall(filter => t.name.contains(filter))) {
      implicit val pos: Position = t.pos
      // safety-net timeout: a single hung test must not stall the whole serial CI run (retry budget is 15 minutes)
      test(t.name)(IO.fromFuture(IO(t.f())).timeout(4.minutes).unsafeToFuture())
    }
  }
  private val release = doRelease

  override protected def afterAll(): Unit = {
    // the resources can only be released after all of the tests are run
    // bounded, so that a wedged backend close fails loudly instead of hanging the non-forked sbt JVM forever
    release.timeout(1.minute).unsafeRunSync()
    // bounded for the same reason (CI retry budget is 15 minutes)
    shutdownDispatcher.timeout(1.minute).unsafeRunSync()
    super.afterAll()
  }
}
