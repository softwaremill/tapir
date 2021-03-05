package sttp.tapir.model

import sttp.model.{Header, ResponseMetadata, StatusCode}

import scala.collection.immutable.Seq

case class ServerResponse[WB, B](code: StatusCode, headers: Seq[Header], body: Option[Either[WB, B]]) extends ResponseMetadata {
  override def statusText: String = ""
}
