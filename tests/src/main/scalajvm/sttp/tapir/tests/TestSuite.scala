package sttp.tapir.tests

import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import org.scalactic.source.Position
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.{BeforeAndAfterAll, Exceptional, FutureOutcome}

import scala.concurrent.Future
import scala.concurrent.duration._

trait TestSuite extends AsyncFunSuite with BeforeAndAfterAll {
  def tests: Resource[IO, List[Test]]
  def testNameFilter: Option[String] = None // define to run a single test (temporarily for debugging)

  /** The number of times a failing test is retried before it is reported as failed. Override with a positive value in
    * suites whose backend is subject to a known, unavoidable flake (e.g. an upstream bug) to keep CI green; keep the
    * override well-commented with the reason. `0` (the default) means each test runs exactly once, as usual.
    */
  def retries: Int = 0

  override def withFixture(test: NoArgAsyncTest): FutureOutcome = withRetries(test, retries)

  private def withRetries(test: NoArgAsyncTest, remaining: Int): FutureOutcome =
    new FutureOutcome(super.withFixture(test).toFuture.flatMap {
      case Exceptional(e) if remaining > 0 =>
        println(s"Test '${test.name}' failed, retrying ($remaining ${if (remaining == 1) "retry" else "retries"} left).")
        e.printStackTrace()
        withRetries(test, remaining - 1).toFuture
      case other => Future.successful(other)
    })

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
