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

  def in_stream_out_string[S](s: Streams[S]): PublicEndpoint[s.BinaryStream, Unit, String, S] = {
    endpoint.post.in("api" / "echo").in(streamTextBody(s)(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8))).out(stringBody)
  }

  def in_stream_out_stream_with_content_length[S](
      s: Streams[S]
  ): PublicEndpoint[(Long, s.BinaryStream), Unit, (Long, s.BinaryStream), S] = {
    val sb = streamTextBody[S](s)(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8))
    endpoint.post.in("api" / "echo").in(header[Long](HeaderNames.ContentLength)).in(sb).out(header[Long](HeaderNames.ContentLength)).out(sb)
  }

  def out_custom_content_type_stream_body[S](s: Streams[S]): PublicEndpoint[(Int, s.BinaryStream), Unit, (String, s.BinaryStream), S] = {
    val sb = streamTextBody[S](s)(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8))
    endpoint.post
      .in(query[Int]("kind"))
      .in(sb)
      .out(header[String](HeaderNames.ContentType))
      .out(sb)
  }

  def in_string_stream_out_either_stream_string[S](
      s: Streams[S]
  ): PublicEndpoint[(String, s.BinaryStream), Unit, Either[s.BinaryStream, String], S] = {
    val sb = streamTextBody(s)(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8))

    endpoint.post
      .in(query[String]("which"))
      .in(sb)
      .out(
        oneOf(
          oneOfVariantClassMatcher(sb.toEndpointIO.map(Left(_))(_.value), classOf[Left[s.BinaryStream, String]]),
          oneOfVariantClassMatcher(stringBody.map(Right(_))(_.value), classOf[Right[s.BinaryStream, String]])
        )
      )
  }

  def in_stream_out_either_json_xml_stream[S](
      s: Streams[S]
  ): PublicEndpoint[s.BinaryStream, Unit, s.BinaryStream, S] = {
    def textStream(format: CodecFormat) = streamTextBody(s)(format, None)

    endpoint.post
      .in(textStream(CodecFormat.TextPlain()))
      .out(
        oneOfBody(textStream(CodecFormat.Json()).toEndpointIO, textStream(CodecFormat.Xml()).toEndpointIO)
      )
  }
}
