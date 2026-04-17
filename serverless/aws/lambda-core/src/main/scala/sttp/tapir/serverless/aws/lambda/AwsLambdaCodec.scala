package sttp.tapir.serverless.aws.lambda

import io.circe._
import io.circe.generic.auto._
import io.circe.parser.{decode => jsonDecode}
import io.circe.syntax._

import java.io.{BufferedWriter, OutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets

/** Shared JSON decode/encode and V1-to-V2 request normalization used by the various Lambda handler implementations. */
private[aws] object AwsLambdaCodec {

  /** Decodes a JSON string into an [[AwsRequest]], normalizing V1 format to V2 if needed. Returns `Left` with an error response for
    * malformed input, or `Right` with the normalized request.
    *
    * @throws IllegalArgumentException
    *   if the decoded type is neither [[AwsRequestV1]] nor [[AwsRequest]]
    */
  def decodeRequest[R: Decoder](json: String): Either[AwsResponse, AwsRequest] =
    jsonDecode[R](json) match {
      case Left(e)                => Left(AwsResponse.badRequest(s"Invalid AWS request: ${e.getMessage}"))
      case Right(r: AwsRequestV1) => Right(r.toV2)
      case Right(r: AwsRequest)   => Right(r)
      case Right(r)               =>
        throw new IllegalArgumentException(s"Request of type ${r.getClass.getCanonicalName} is not supported")
    }

  /** Encodes an [[AwsResponse]] as a JSON string. */
  def encodeResponse(response: AwsResponse): String =
    Printer.noSpaces.print(response.asJson)

  /** Writes an [[AwsResponse]] as JSON to the given output stream. */
  def writeResponse(response: AwsResponse, output: OutputStream): Unit = {
    val writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))
    try writer.write(encodeResponse(response))
    finally {
      writer.flush()
      writer.close()
    }
  }
}
