package sttp.tapir.server.ziohttp

import sttp.tapir.server.interpreter.BodyListener
import zio.http.FormField
import zio.stream.ZStream
import zio.{Cause, RIO, ZIO}

import scala.util.{Failure, Success, Try}

private[ziohttp] class ZioHttpBodyListener[R] extends BodyListener[RIO[R, *], ZioResponseBody] {
  override def onComplete(body: ZioResponseBody)(cb: Try[Unit] => RIO[R, Unit]): RIO[R, ZioResponseBody] =
    ZIO
      .environmentWithZIO[R]
      .apply { r =>
        def succeed = cb(Success(())).provideEnvironment(r)
        def failed(cause: Cause[Throwable]) = cb(Failure(cause.squash)).orDie.provideEnvironment(r)

        body match {
          case Right(ZioStreamHttpResponseBody(stream, contentLength)) =>
            ZIO.right(
              ZioStreamHttpResponseBody(
                stream.onError(failed) ++ ZStream.fromZIO(succeed).drain,
                contentLength
              )
            )
          // We can only attach callbacks to streams; if all form fields are "strict" (known upfront), there's no hook
          // we could use. Hence attaching the callbacks to the last streaming form field, if one exists.
          case mp @ Right(ZioMultipartHttpResponseBody(formFields)) =>
            formFields.collect { case ff: FormField.StreamingBinary => ff }.lastOption match {
              case Some(lastStreamingBinary) =>
                val modifiedFormFields = formFields.map { ff =>
                  if (ff.eq(lastStreamingBinary)) {
                    val sff = ff.asInstanceOf[FormField.StreamingBinary]
                    sff.copy(data = sff.data.onError(failed) ++ ZStream.fromZIO(succeed).drain.orDie)
                  } else {
                    ff
                  }
                }
                ZIO.right(ZioMultipartHttpResponseBody(modifiedFormFields))
              case None => succeed.as(mp)
            }

          case rawOrWs => succeed.as(rawOrWs)
        }
      }
}
