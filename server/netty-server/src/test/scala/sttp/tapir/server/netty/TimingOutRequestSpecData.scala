package sttp.tapir.server.netty

import cats.effect.IO
import cats.effect.kernel.Resource
import io.netty.channel.EventLoopGroup
import sttp.tapir._

import java.net.Socket
import java.nio.charset.StandardCharsets.US_ASCII
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}

class TimingOutRequestSpecData(eventLoopGroup: EventLoopGroup)(implicit ec: ExecutionContext) {

  private val shortRequestTimeout = 1.second

  val pauseBetweenWrites: FiniteDuration = shortRequestTimeout / 10

  val bodyFragment: Array[Byte] = "test".getBytes(US_ASCII)

  private val StatusLine = """HTTP/1\.1 \d{3} [^\r\n]*""".r

  def requestHead(port: Int, contentLength: Int = 10000): Array[Byte] =
    s"PUT / HTTP/1.1\r\nHost: localhost:$port\r\nContent-Type: text/plain\r\nContent-Length: $contentLength\r\n\r\n"
      .getBytes(US_ASCII)

  def send(socket: Socket, bytes: Array[Byte]): IO[Unit] =
    IO.blocking {
      socket.getOutputStream.write(bytes)
      socket.getOutputStream.flush()
    }

  def statusLinesForTimingOutRequest(writeRequest: (Socket, Int) => IO[Unit]): IO[List[String]] = {
    val e = endpoint.put
      .in(stringBody)
      .out(stringBody)
      .serverLogicSuccess[Future](body => Future.successful(body))

    val serverConfig = NettyConfig.default
      .eventLoopGroup(eventLoopGroup)
      .randomPort
      .withDontShutdownEventLoopGroupOnClose
      .noGracefulShutdown
      .requestTimeout(shortRequestTimeout)

    val bind = IO.fromFuture(IO.delay(NettyFutureServer(serverConfig).addEndpoints(List(e)).start()))

    Resource
      .make(bind)(server => IO.fromFuture(IO.delay(server.stop())))
      .map(_.port)
      .use { port =>
        Resource.fromAutoCloseable(IO(clientSocket(port))).use { socket =>
          for {
            _ <- writeRequest(socket, port)
            written <- IO.blocking(new String(socket.getInputStream.readAllBytes(), US_ASCII))
          } yield StatusLine.findAllIn(written).toList
        }
      }
  }

  private def clientSocket(port: Int): Socket = {
    val socket = new Socket("localhost", port)
    socket.setSoTimeout((shortRequestTimeout * 20).toMillis.toInt)
    socket
  }
}
