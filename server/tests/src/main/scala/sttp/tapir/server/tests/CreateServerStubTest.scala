package sttp.tapir.server.tests

import sttp.client4.testing.{BackendStub, StreamBackendStub}
import sttp.tapir.server.interceptor.CustomiseInterceptors

import scala.concurrent.Future

trait CreateServerStubTest[F[_], OPTIONS] {
  def customiseInterceptors: CustomiseInterceptors[F, OPTIONS]
  def stub: BackendStub[F]
  def asFuture[A]: F[A] => Future[A]
  def cleanUp(): Unit = ()
}
