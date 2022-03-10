package sttp.tapir.model

import sttp.model.{Header, Headers, ResponseMetadata, StatusCode}

import scala.collection.immutable.Seq

trait ServerResponse[+B] extends ResponseMetadata {
  def code: StatusCode
  def headers: Seq[Header]
  def body: Option[B]

  override def statusText: String = ""
  override def toString: String = s"ServerResponse($code,${Headers.toStringSafe(headers)})"

  def showShort: String = code.toString()
  def showCodeAndHeaders: String = s"$code (${Headers.toStringSafe(headers)})"
}

object ServerResponse {
  def notFound[B]: ServerResponse[B] = new ServerResponse[B] {
    override def code: StatusCode = StatusCode.NotFound
    override def headers: Seq[Header] = Nil
    override def body: Option[B] = None
  }
}
