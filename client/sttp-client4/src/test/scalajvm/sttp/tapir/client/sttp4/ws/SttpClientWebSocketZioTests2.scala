//package sttp.tapir.client.sttp4.ws
//
//import cats.effect.IO
//import sttp.capabilities.WebSockets
//import sttp.capabilities.zio.ZioStreams
//import sttp.client4._
//import sttp.client4.httpclient.zio.HttpClientZioBackend
//import sttp.tapir.client.sttp4.ws.zio.Runtime.default
//import sttp.tapir.client.sttp4.ws.zio.{CancelableFuture, Task, Unsafe}
//import sttp.tapir.client.sttp4.{GenericRequestExtensions, WebSocketToPipe}
//import sttp.tapir.client.tests.ClientTests
//import sttp.tapir.{DecodeResult, Endpoint}
//
//abstract class SttpClientWebSocketZioTests2 extends ClientTests[Any] {
//  private val runtime: default.UnsafeAPI = zio.Runtime.default.unsafe
//  val backend: Backend[Task] = unsafeRun(HttpClientZioBackend())
//  def wsToPipe: WebSocketToPipe[R]
//
//  override def send[A, I, E, O](
//      e: Endpoint[A, I, E, O, R],
//      port: Port,
//      securityArgs: A,
//      args: I,
//      scheme: String = "http"
//  ): IO[Either[E, O]] =
//    IO.fromFuture(IO.delay {
//      implicit val wst: WebSocketToPipe[R] = wsToPipe
//      val genReq = SttpClientInterpreter()
//        .toSecureRequestThrowDecodeFailures(e, Some(uri"$scheme://localhost:$port"))
//        .apply(securityArgs)
//        .apply(args)
//
//      val send = GenericRequestExtensions.sendRequest(backend, genReq).map(_.body)
//      unsafeToFuture(send).future
//    })
//
//  override def safeSend[A, I, E, O](
//      e: Endpoint[A, I, E, O, R],
//      port: Port,
//      securityArgs: A,
//      args: I
//  ): IO[DecodeResult[Either[E, O]]] =
//    IO.fromFuture(IO.delay {
//      implicit val wst: WebSocketToPipe[R] = wsToPipe
//      val genReq = SttpClientInterpreter()
//        .toSecureRequest(e, Some(uri"http://localhost:$port"))
//        .apply(securityArgs)
//        .apply(args)
//
//      val send = GenericRequestExtensions.sendRequest(backend, genReq).map(_.body)
//      unsafeToFuture(send).future
//    })
//
//  override protected def afterAll(): Unit = {
//    unsafeRun(backend.close())
//    super.afterAll()
//  }
//
//  def unsafeRun[T](task: Task[T]): T =
//    Unsafe.unsafe { implicit u =>
//      runtime.run(task).getOrThrowFiberFailure()
//    }
//
//  def unsafeToFuture[T](task: Task[T]): CancelableFuture[T] =
//    Unsafe.unsafe(implicit u => runtime.runToFuture(task))
//
//}
