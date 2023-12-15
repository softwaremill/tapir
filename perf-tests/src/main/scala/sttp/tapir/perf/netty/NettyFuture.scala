package sttp.tapir.perf.netty

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import sttp.tapir.server.ServerEndpoint
import scala.concurrent.Future
import sttp.tapir.perf.apis.SimpleGetEndpoints
import sttp.monad.MonadError
import sttp.monad.FutureMonad
import scala.concurrent.ExecutionContext
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding}
import ExecutionContext.Implicits.global

object Tapir extends SimpleGetEndpoints {

  implicit val mErr: MonadError[Future] = new FutureMonad()(ExecutionContext.Implicits.global)

  val serverEndpointGens = replyingWithDummyStr[Future](
    List(gen_get_in_string_out_string, gen_post_in_string_out_string, gen_post_in_file_out_string)
  )

  def genEndpoints(i: Int) = genServerEndpoints(serverEndpointGens)(i).toList
}

object NettyFuture {

  def runServer(endpoints: List[ServerEndpoint[Any, Future]]): Unit = {
    val declaredPort = 8080
    val declaredHost = "0.0.0.0"
    // Starting netty server
    val serverBinding: NettyFutureServerBinding =
      Await.result(
        NettyFutureServer()
          .port(declaredPort)
          .host(declaredHost)
          .addEndpoints(endpoints)
          .start(),
        Duration.Inf
      )
  }
}

object TapirMultiServer extends App { NettyFuture.runServer(Tapir.genEndpoints(128)) }
