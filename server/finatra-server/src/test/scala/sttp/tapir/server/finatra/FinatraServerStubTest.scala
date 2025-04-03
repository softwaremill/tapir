package sttp.tapir.server.finatra

import com.twitter.util.Future
import sttp.client4.testing.BackendStub
import sttp.tapir.server.finatra.FinatraServerInterpreter.FutureMonadError
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubTest}

import scala.concurrent.Promise

object FinatraCreateServerStubTest extends CreateServerStubTest[Future, FinatraServerOptions] {
  override def customiseInterceptors: CustomiseInterceptors[Future, FinatraServerOptions] = FinatraServerOptions.customiseInterceptors
  override def stub: BackendStub[Future] = BackendStub(FutureMonadError)
  override def asFuture[A]: Future[A] => concurrent.Future[A] = f => {
    val p = Promise[A]
    f.onFailure(p.failure)
    f.onSuccess(p.success)
    p.future
  }
}

class FinatraServerStubTest extends ServerStubTest(FinatraCreateServerStubTest)
