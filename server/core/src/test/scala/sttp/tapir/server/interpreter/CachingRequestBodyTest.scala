package sttp.tapir.server.interpreter

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.Streams
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity
import sttp.tapir._
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.TestUtil.createTestRequest

import java.io.InputStream
import java.nio.charset.StandardCharsets

class CachingRequestBodyTest extends AnyFlatSpec with Matchers {
  private implicit val idMonad: MonadError[Identity] = IdentityMonad

  private class CountingRequestBody(content: String) extends RequestBody[Identity, NoStreams] {
    var reads = 0
    var lastMaxBytes: Option[Long] = None
    override val streams: Streams[NoStreams] = NoStreams
    override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): RawValue[R] = {
      reads += 1
      lastMaxBytes = maxBytes
      bodyType match {
        case RawBodyType.ByteArrayBody => RawValue(content.getBytes(StandardCharsets.UTF_8)).asInstanceOf[RawValue[R]]
        case other                     => throw new IllegalStateException(s"unexpected body type: $other")
      }
    }
    override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
      throw new IllegalStateException("should not be called")
  }

  private val request = createTestRequest(List("test"))

  it should "read the delegate only once for two string reads" in {
    val delegate = new CountingRequestBody("hello")
    val caching = new CachingRequestBody[Identity, NoStreams](delegate)

    caching.toRaw(request, RawBodyType.StringBody(StandardCharsets.UTF_8), None).value shouldBe "hello"
    caching.toRaw(request, RawBodyType.StringBody(StandardCharsets.UTF_8), None).value shouldBe "hello"

    delegate.reads shouldBe 1
  }

  it should "serve different bytes-like representations from one read" in {
    val delegate = new CountingRequestBody("abc")
    val caching = new CachingRequestBody[Identity, NoStreams](delegate)

    caching.toRaw(request, RawBodyType.StringBody(StandardCharsets.UTF_8), None).value shouldBe "abc"
    caching.toRaw(request, RawBodyType.ByteArrayBody, None).value shouldBe "abc".getBytes(StandardCharsets.UTF_8)
    caching.toRaw(request, RawBodyType.ByteBufferBody, None).value.array() shouldBe "abc".getBytes(StandardCharsets.UTF_8)

    val stream: InputStream = caching.toRaw(request, RawBodyType.InputStreamBody, None).value
    new String(stream.readAllBytes(), StandardCharsets.UTF_8) shouldBe "abc"

    val range = caching.toRaw(request, RawBodyType.InputStreamRangeBody, None).value
    new String(range.inputStream().readAllBytes(), StandardCharsets.UTF_8) shouldBe "abc"

    delegate.reads shouldBe 1
  }

  it should "give a fresh input stream on each read" in {
    val delegate = new CountingRequestBody("xy")
    val caching = new CachingRequestBody[Identity, NoStreams](delegate)

    val first: InputStream = caching.toRaw(request, RawBodyType.InputStreamBody, None).value
    new String(first.readAllBytes(), StandardCharsets.UTF_8) shouldBe "xy"

    val second: InputStream = caching.toRaw(request, RawBodyType.InputStreamBody, None).value
    new String(second.readAllBytes(), StandardCharsets.UTF_8) shouldBe "xy"
  }

  it should "not let mutating a returned byte array corrupt the cache" in {
    val delegate = new CountingRequestBody("hello")
    val caching = new CachingRequestBody[Identity, NoStreams](delegate)

    val first = caching.toRaw(request, RawBodyType.ByteArrayBody, None).value
    java.util.Arrays.fill(first, 'X'.toByte)

    caching.toRaw(request, RawBodyType.ByteArrayBody, None).value shouldBe "hello".getBytes(StandardCharsets.UTF_8)
    caching.toRaw(request, RawBodyType.StringBody(StandardCharsets.UTF_8), None).value shouldBe "hello"

    delegate.reads shouldBe 1
  }

  it should "pass maxBytes through to the delegate on the first read" in {
    val delegate = new CountingRequestBody("hello")
    val caching = new CachingRequestBody[Identity, NoStreams](delegate)

    caching.toRaw(request, RawBodyType.StringBody(StandardCharsets.UTF_8), Some(1024L)).value shouldBe "hello"
    delegate.lastMaxBytes shouldBe Some(1024L)

    caching.toRaw(request, RawBodyType.StringBody(StandardCharsets.UTF_8), Some(2048L)).value shouldBe "hello"
    delegate.reads shouldBe 1
    delegate.lastMaxBytes shouldBe Some(1024L)
  }

  it should "not let mutating a returned byte buffer corrupt the cache" in {
    val delegate = new CountingRequestBody("hello")
    val caching = new CachingRequestBody[Identity, NoStreams](delegate)

    val first = caching.toRaw(request, RawBodyType.ByteBufferBody, None).value
    while (first.hasRemaining) { val _ = first.put('X'.toByte) }

    caching.toRaw(request, RawBodyType.ByteBufferBody, None).value.array() shouldBe "hello".getBytes(StandardCharsets.UTF_8)
    caching.toRaw(request, RawBodyType.StringBody(StandardCharsets.UTF_8), None).value shouldBe "hello"

    delegate.reads shouldBe 1
  }
}
