package sttp.tapir.server.netty

import java.net.{URI => JavaUri}

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.Method
import sttp.tapir.server.netty.internal.RichNettyHttpHeaders

class NettyServerRequestSpec extends AnyFreeSpec with Matchers {
  val uri: JavaUri = JavaUri.create("/with%20space/another/last?param=value1&param=value2")

  val headers = new DefaultHttpHeaders()
  headers.add(HttpHeaderNames.CONTENT_TYPE, "text/plain")

  // https://datatracker.ietf.org/doc/html/rfc7230#section-4.1.2
  val trailingHeaders = new DefaultHttpHeaders()
  trailingHeaders.add("header-generated-during-sending", "something")

  val emptyPostRequest = new DefaultFullHttpRequest(
    HttpVersion.HTTP_1_1,
    HttpMethod.POST,
    uri.toString,
    Unpooled.EMPTY_BUFFER,
    headers,
    trailingHeaders
  )

  // Use EmbeddedChannel for testing - it provides a ChannelHandlerContext
  private val embeddedChannel = new EmbeddedChannel()
  private var capturedCtx: io.netty.channel.ChannelHandlerContext = null

  embeddedChannel
    .pipeline()
    .addLast(new io.netty.channel.ChannelInboundHandlerAdapter {
      override def channelActive(ctx: io.netty.channel.ChannelHandlerContext): Unit = {
        capturedCtx = ctx
        super.channelActive(ctx)
      }
    })
    .fireChannelActive()

  val nettyServerRequest: NettyServerRequest = NettyServerRequest(emptyPostRequest, capturedCtx)

  "uri is the same as in request" in {
    nettyServerRequest.uri.toString should equal(uri.toString)
  }

  "headers check" - {
    nettyServerRequest.headers should have size 2

    "normal headers are not lost" in {
      val header = headers.toHeaderSeq.head

      nettyServerRequest.headers should contain(header)
    }

    "trailing headers are not lost" in {
      val trailingHeader = trailingHeaders.toHeaderSeq.head

      nettyServerRequest.headers should contain(trailingHeader)
    }
  }

  "query duplicated query param values are not lost" in {
    nettyServerRequest.queryParameters.getMulti("param").get should contain theSameElementsAs Seq("value2", "value1")
  }

  "path segments are decoded and in order" in {
    nettyServerRequest.pathSegments should contain inOrderOnly ("with space", "another", "last")
  }

  "method check" in {
    nettyServerRequest.method should equal(Method.POST)
  }
}
