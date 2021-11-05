package sttp.tapir.tests

import sttp.capabilities.Streams
import sttp.model.HeaderNames
import sttp.tapir._

import java.nio.charset.StandardCharsets

object Streaming {
  def in_stream_out_stream[S](s: Streams[S]): PublicEndpoint[s.BinaryStream, Unit, s.BinaryStream, S] = {
    val sb = streamTextBody(s)(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8))
    endpoint.post.in("api" / "echo").in(sb).out(sb)
  }

  def in_stream_out_stream_with_content_length[S](
      s: Streams[S]
  ): PublicEndpoint[(Long, s.BinaryStream), Unit, (Long, s.BinaryStream), S] = {
    val sb = streamTextBody[S](s)(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8))
    endpoint.post.in("api" / "echo").in(header[Long](HeaderNames.ContentLength)).in(sb).out(header[Long](HeaderNames.ContentLength)).out(sb)
  }
}
