package sttp.tapir.server.tests

import sttp.client3.testing.SttpBackendStub
import sttp.tapir.server.interceptor.CustomiseInterceptors

import scala.concurrent.Future

trait CreateServerStubTest[F[_], OPTIONS] {
  def customiseInterceptors: CustomiseInterceptors[F, OPTIONS]
  def stub[R]: SttpBackendStub[F, R]
  def asFuture[A]: F[A] => Future[A]
  def cleanUp(): Unit = ()
}
