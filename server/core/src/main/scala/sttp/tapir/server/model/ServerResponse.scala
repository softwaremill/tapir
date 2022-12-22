package sttp.tapir.server.model

import sttp.model.{Header, Headers, ResponseMetadata, StatusCode}

import scala.collection.immutable.Seq

/** @param source The output, from which this response has been created. */
case class ServerResponse[+B](code: StatusCode, headers: Seq[Header], body: Option[B], source: Option[ValuedEndpointOutput[_]])
    extends ResponseMetadata {
  override def statusText: String = ""
  override def toString: String = s"ServerResponse($code,${Headers.toStringSafe(headers)})"

  def showShort: String = code.toString()
  def showCodeAndHeaders: String = s"$code (${Headers.toStringSafe(headers)})"
  def addHeaders(additionalHeaders: Seq[Header]): ServerResponse[B] = copy(headers = headers ++ additionalHeaders)
}

object ServerResponse {
  def notFound[B]: ServerResponse[B] = ServerResponse[B](StatusCode.NotFound, Nil, None, None)
}
