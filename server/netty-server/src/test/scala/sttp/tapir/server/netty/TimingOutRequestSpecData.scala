package sttp.tapir.server.netty

import cats.effect.IO
import cats.effect.kernel.Resource
import io.netty.channel.EventLoopGroup
import sttp.tapir._

import java.io.{BufferedReader, InputStreamReader}
import java.net.Socket
import java.nio.charset.StandardCharsets.US_ASCII
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future, blocking}

class TimingOutRequestSpecData(eventLoopGroup: EventLoopGroup)(implicit ec: ExecutionContext) {

  val completeBody: Array[Byte] = "test".getBytes(US_ASCII)
  val slowBody: Array[Byte] = "slow".getBytes(US_ASCII)

  private val shortRequestTimeout = 1.second
  private val socketReadTimeout = shortRequestTimeout * 20
  private val slowBodyString = new String(slowBody, US_ASCII)

  def requestHead(port: Int, contentLength: Int): Array[Byte] =
    s"PUT / HTTP/1.1\r\nHost: localhost:$port\r\nContent-Type: text/plain\r\nContent-Length: $contentLength\r\n\r\n"
      .getBytes(US_ASCII)

  def incompleteRequestHead(port: Int): Array[Byte] = requestHead(port, contentLength = 10000)

  def send(socket: Socket, bytes: Array[Byte]): IO[Unit] =
    IO.blocking {
      socket.getOutputStream.write(bytes)
      socket.getOutputStream.flush()
    }

  def readStatusLine(socket: Socket): IO[String] = IO.blocking {
    val in = new BufferedReader(new InputStreamReader(socket.getInputStream, US_ASCII))
    val statusLine = in.readLine()
    val headers = Iterator.continually(in.readLine()).takeWhile(_.nonEmpty).toList

    if (headers.exists(_.toLowerCase.contains("chunked"))) {
      var chunkSize = in.readLine()
      while (chunkSize != "0") {
        in.readLine()
        chunkSize = in.readLine()
      }
      in.readLine()
    }
    statusLine
  }

  /** Runs `interact` against a server with a short request timeout. A request with `slowBody` is only answered after `interact` completes,
    * so the request timeout is guaranteed to fire first, regardless of how long the JVM is stalled for.
    */
  def statusLinesFromShortTimeoutServer(interact: (Socket, Int) => IO[List[String]]): IO[List[String]] = {
    val interactionDone = new CountDownLatch(1)
    val e = endpoint.put
      .in(stringBody)
      .out(stringBody)
      .serverLogicSuccess[Future] { body =>
        if (body == slowBodyString) awaitLatch(interactionDone)
        Future.successful(body)
      }

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
        Resource
          .fromAutoCloseable(IO(clientSocket(port)))
          .use { socket =>
            interact(socket, port)
          }
          .guarantee(IO(interactionDone.countDown()))
      }
  }

  /** Blocks server logic until the latch is released; bounded, so that a failing test doesn't leave a thread blocked forever. */
  def awaitLatch(latch: CountDownLatch): Unit = blocking {
    val _ = latch.await(socketReadTimeout.toMillis, TimeUnit.MILLISECONDS)
  }

  private def clientSocket(port: Int): Socket = {
    val socket = new Socket("localhost", port)
    socket.setSoTimeout(socketReadTimeout.toMillis.toInt)
    socket
  }
}
