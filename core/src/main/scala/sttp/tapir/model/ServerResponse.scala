package sttp.tapir.model

import sttp.model.{Header, Headers, ResponseMetadata, StatusCode}
import sttp.tapir.server.ValuedEndpointOutput

import scala.collection.immutable.Seq

/** @param source The output using which this response has been created, if any. */
case class ServerResponse[+B](
    code: StatusCode,
    headers: Seq[Header],
    body: Option[B],
    source: Option[ValuedEndpointOutput[_]]
) extends ResponseMetadata {
  override def statusText: String = ""
  override def toString: String = s"ServerResponse($code,${Headers.toStringSafe(headers)})"
}
