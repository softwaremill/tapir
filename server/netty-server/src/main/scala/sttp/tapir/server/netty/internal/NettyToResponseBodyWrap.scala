package sttp.tapir.server.netty.internal

import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.Publisher
import sttp.tapir.server.netty.internal.NettyToResponseBody.DefaultChunkSize
import sttp.tapir.server.netty.internal.reactivestreams.FileRangePublisher
import sttp.tapir.{FileRange, InputStreamRange}

import java.io.InputStream

/** This trait includes methods for wrapping `FileRange` and `InputStream` types into `Publisher[HttpContent]`, which are essential for
  * reactive streaming in Netty.
  */
trait NettyToResponseBodyWrap[S] extends NettyToResponseBodyCommon[S] {

  protected def wrap(fileRange: FileRange): Publisher[HttpContent] =
    new FileRangePublisher(fileRange, DefaultChunkSize)

  protected def wrap(content: InputStream): Publisher[HttpContent] =
    wrap(InputStreamRange(() => content, range = None))

}
