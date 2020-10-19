package sttp.tapir.docs.asyncapi

import sttp.model.MediaType
import sttp.tapir.EndpointOutput.WebSocketBodyWrapper
import sttp.tapir.apispec.IterableToListMap
import sttp.tapir.asyncapi.{Message, SingleMessage}
import sttp.tapir.docs.apispec.schema.{ObjectTypeData, Schemas, calculateUniqueKeys, objectInfoToName}
import sttp.tapir.{Codec, CodecFormat, Schema => TSchema, SchemaType => TSchemaType}

import scala.collection.immutable.ListMap

private[asyncapi] class MessagesForEndpoints(schemas: Schemas) {
  private type CodecData = Either[(TSchemaType.SObjectInfo, MediaType), TSchema[_]]

  def apply[P[_, _]](
      wss: Iterable[WebSocketBodyWrapper[P, _, _, _]]
  ): (Map[Codec[_, _, _ <: CodecFormat], MessageKey], ListMap[MessageKey, Message]) = {

    val codecs: Iterable[Codec[_, _, _ <: CodecFormat]] = wss.map(ws => ws.wrapped.requests) ++ wss.map(ws => ws.wrapped.responses)
    val codecToData: ListMap[Codec[_, _, _ <: CodecFormat], CodecData] = codecs.toList.map(c => c -> toData(c)).toListMap

    val dataToKey = calculateUniqueKeys(codecToData.values.toSet, dataToName)
    val codecToKey = codecToData.map { case (codec, data) => codec -> dataToKey(data) }.toMap[Codec[_, _, _ <: CodecFormat], String]
    val keyToMessage = codecToData.map { case (codec, data) => dataToKey(data) -> message(codec) }

    (codecToKey, keyToMessage)
  }

  private def toData(codec: Codec[_, _, _ <: CodecFormat]): CodecData =
    ObjectTypeData(codec).headOption match { // the first element, if any, corresponds to the object
      case Some(otd) => Left((otd._1, codec.format.mediaType))
      case None      => Right(codec.schema.getOrElse(TSchema.schemaForByteArray).copy(description = None, deprecated = false))
    }

  private def message(codec: Codec[_, _, _ <: CodecFormat]): Message = {
    SingleMessage(
      None,
      Some(Right(schemas(codec))),
      None,
      None,
      Some(codec.format.mediaType.toString()),
      None,
      None,
      None,
      codec.schema.flatMap(_.description),
      Nil,
      None,
      Nil,
      ListMap.empty,
      Nil
    )
  }

  private def dataToName(d: CodecData): String =
    d match {
      case Left((oi, _)) => objectInfoToName(oi)
      case Right(schema) => schema.schemaType.show
    }
}
