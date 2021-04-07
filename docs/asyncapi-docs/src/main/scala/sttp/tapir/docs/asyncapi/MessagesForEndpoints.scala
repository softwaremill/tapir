package sttp.tapir.docs.asyncapi

import sttp.model.MediaType
import sttp.tapir.EndpointOutput.WebSocketBodyWrapper
import sttp.tapir.SchemaType.SObjectInfo
import sttp.tapir.asyncapi.{Message, SingleMessage}
import sttp.tapir.docs.apispec.schema.{Schemas, ToObjectSchema, calculateUniqueKeys}
import sttp.tapir.internal.IterableToListMap
import sttp.tapir.{Codec, CodecFormat, EndpointIO, WebSocketBodyOutput, Schema => TSchema, SchemaType => TSchemaType}
import sttp.ws.WebSocketFrame

import scala.collection.immutable.ListMap

private[asyncapi] class MessagesForEndpoints(schemas: Schemas, schemaName: SObjectInfo => String, toObjectSchema: ToObjectSchema) {
  private type CodecData = Either[(TSchemaType.SObjectInfo, MediaType), TSchema[_]]

  private case class CodecWithInfo[T](codec: Codec[WebSocketFrame, T, _ <: CodecFormat], info: EndpointIO.Info[T])

  def apply[P[_, _]](
      wss: Iterable[WebSocketBodyWrapper[_, _]]
  ): (Map[Codec[_, _, _ <: CodecFormat], MessageKey], ListMap[MessageKey, Message]) = {

    val codecs: Iterable[CodecWithInfo[_]] = wss.flatMap(ws => codecsFor(ws.wrapped))
    val codecToData: ListMap[CodecWithInfo[_], CodecData] = codecs.toList.map(ci => ci -> toData(ci.codec)).toListMap

    val dataToKey = calculateUniqueKeys(codecToData.values.toSet, dataToName)
    val codecToKey = codecToData.map { case (ci, data) => ci.codec -> dataToKey(data) }.toMap[Codec[_, _, _ <: CodecFormat], String]
    val keyToMessage = codecToData.map { case (ci, data) => dataToKey(data) -> message(ci) }

    (codecToKey, keyToMessage)
  }

  private def toData(codec: Codec[_, _, _ <: CodecFormat]): CodecData =
    toObjectSchema.apply(codec).headOption match { // the first element, if any, corresponds to the object
      case Some(os) => Left((os._1, codec.format.mediaType))
      case None     => Right(codec.schema.copy(description = None, deprecated = false))
    }

  private def codecsFor[REQ, RESP](w: WebSocketBodyOutput[_, REQ, RESP, _, _]): Iterable[CodecWithInfo[_]] = List(
    CodecWithInfo(w.requests, w.requestsInfo),
    CodecWithInfo(w.responses, w.responsesInfo)
  )

  private def message[T](ci: CodecWithInfo[T]): Message = {
    val convertedExamples = ExampleConverter.convertExamples(ci.codec, ci.info.examples)
    SingleMessage(
      None,
      Some(Right(schemas(ci.codec))),
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
      if (convertedExamples.isEmpty) Nil else List(Map("payload" -> convertedExamples)),
      Nil
    )
  }

  private def dataToName(d: CodecData): String =
    d match {
      case Left((oi, _)) => schemaName(oi)
      case Right(schema) => schema.schemaType.show
    }
}
