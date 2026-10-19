package sttp.tapir.docs.asyncapi

import sttp.apispec.asyncapi.{Message, SingleMessage}
import sttp.model.MediaType
import sttp.tapir.EndpointOutput.WebSocketBodyWrapper
import sttp.tapir.Schema.SName
import sttp.tapir.docs.apispec.schema.{TSchemaToASchema, ToKeyedSchemas, calculateUniqueIds}
import sttp.tapir.internal.IterableToListMap
import sttp.tapir.{Codec, CodecFormat, EndpointIO, WebSocketBodyOutput, Schema => TSchema}
import sttp.ws.WebSocketFrame

import scala.collection.immutable.ListMap

private[asyncapi] class MessagesForEndpoints(tschemaToASchema: TSchemaToASchema, schemaName: SName => String) {
  private type CodecData = Either[(SName, MediaType), TSchema[?]]

  private case class CodecWithInfo[T](codec: Codec[WebSocketFrame, T, ? <: CodecFormat], info: EndpointIO.Info[T])

  def apply(wss: Iterable[WebSocketBodyWrapper[?, ?]]): (Map[Codec[?, ?, ? <: CodecFormat], MessageKey], ListMap[MessageKey, Message]) = {
    val codecs: Iterable[CodecWithInfo[?]] = wss.flatMap(ws => codecsFor(ws.wrapped))
    val codecToData: ListMap[CodecWithInfo[?], CodecData] = codecs.toList.map(ci => ci -> toData(ci.codec)).toListMap

    val dataToKey = calculateUniqueIds(codecToData.values.toList.distinct, dataToName, failOnDuplicateName = false)
    val codecToKey = codecToData.map { case (ci, data) => ci.codec -> dataToKey(data) }.toMap[Codec[?, ?, ? <: CodecFormat], String]
    val keyToMessage = codecToData.map { case (ci, data) => dataToKey(data) -> message(ci) }

    (codecToKey, keyToMessage)
  }

  private def toData(codec: Codec[?, ?, ? <: CodecFormat]): CodecData =
    ToKeyedSchemas.apply(codec).headOption match { // the first element, if any, corresponds to the object
      case Some(os) => Left((os._1.name, codec.format.mediaType))
      case None     => Right(codec.schema.copy(description = None, deprecated = false))
    }

  private def codecsFor[REQ, RESP](w: WebSocketBodyOutput[?, REQ, RESP, ?, ?]): Iterable[CodecWithInfo[?]] = List(
    CodecWithInfo(w.requests, w.requestsInfo),
    CodecWithInfo(w.responses, w.responsesInfo)
  )

  private def message[T](ci: CodecWithInfo[T]): Message = {
    SingleMessage(
      None,
      Some(Right(tschemaToASchema(ci.codec))),
      None,
      None,
      Some(ci.codec.format.mediaType.toString()),
      None,
      None,
      None,
      ci.info.description.orElse(ci.codec.schema.description),
      Nil,
      None,
      Nil,
      ExampleConverter.convertExamples(ci.codec, ci.info.examples),
      Nil
    )
  }

  private def dataToName(d: CodecData): String =
    d match {
      case Left((oi, _)) => schemaName(oi)
      case Right(schema) => schema.schemaType.show
    }
}
