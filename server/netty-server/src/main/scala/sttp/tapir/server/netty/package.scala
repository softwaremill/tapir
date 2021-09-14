package sttp.tapir.server

import io.netty.buffer.ByteBuf
import sttp.tapir.model.ServerResponse

import scala.concurrent.Future

package object netty {
  type Route = NettyServerRequest => Future[Option[ServerResponse[ByteBuf]]]
}
