package sttp.tapir.server.tests

import cats.effect.IO

import java.net.{Socket, SocketTimeoutException}
import java.nio.charset.StandardCharsets

/** For tests which need to see when exactly the server sends frames, or closes the connection. */
object RawWebSocket {
  val CloseOpcode = 0x8

  def handshake(version: Int = 13): Array[Byte] = ("GET / HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
    s"Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: $version\r\n\r\n").getBytes(StandardCharsets.US_ASCII)

  /** A masked close frame, without a payload. */
  val clientClose: Array[Byte] = Array[Byte]((0x80 | CloseOpcode).toByte, 0x80.toByte, 0, 0, 0, 0)

  /** Reads a frame with a payload shorter than 126 bytes, returning its opcode and payload. */
  def readFrame(socket: Socket): IO[(Int, Array[Byte])] = IO.blocking {
    val in = socket.getInputStream
    val first = in.read()
    if (first == -1) throw new IllegalStateException("Connection closed")
    (first & 0x0f, in.readNBytes(in.read() & 0x7f))
  }

  def closeStatus(payload: Array[Byte]): Int = ((payload(0) & 0xff) << 8) | (payload(1) & 0xff)

  /** Checks that neither a frame, nor the connection's end arrives for a while. */
  def nothingReceived(socket: Socket): IO[Boolean] = IO.blocking {
    val timeout = socket.getSoTimeout
    socket.setSoTimeout(500)
    try { socket.getInputStream.read(); false }
    catch { case _: SocketTimeoutException => true }
    finally socket.setSoTimeout(timeout)
  }
}
