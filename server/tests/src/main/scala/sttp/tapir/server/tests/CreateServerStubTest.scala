package sttp.tapir.server.tests

import sttp.client3.testing.SttpBackendStub
import sttp.tapir.server.interceptor.CustomInterceptors

import scala.concurrent.Future

trait CreateServerStubTest[F[_], OPTIONS] {
  def customInterceptors: CustomInterceptors[F, OPTIONS]
  def stub[R]: SttpBackendStub[F, R]
  def asFuture[A]: F[A] => Future[A]
  def cleanUp(): Unit = ()
}
