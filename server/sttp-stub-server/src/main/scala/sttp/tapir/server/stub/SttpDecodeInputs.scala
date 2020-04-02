package sttp.tapir.server.stub

import sttp.client.{Request, StreamBody}
import sttp.model.{Method, MultiQueryParams}
import sttp.model.Uri.QuerySegment
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.internal.DecodeInputsContext

class SttpDecodeInputs(r: Request[_, _], segmentIndex: Int = 0) extends DecodeInputsContext {
  override def method: Method = Method(r.method.method)

  override def nextPathSegment: (Option[String], DecodeInputsContext) =
    (r.uri.pathSegments.drop(segmentIndex).headOption.map(_.v), new SttpDecodeInputs(r, segmentIndex + 1))

  override def header(name: String): List[String] = r.headers.filter(_.name.toUpperCase == name.toUpperCase).map(_.value).toList

  override def headers: Seq[(String, String)] = r.headers.map(h => (h.name, h.value))

  override def queryParameter(name: String): Seq[String] =
    r.uri.querySegments
      .collect {
        case qp @ QuerySegment.KeyValue(k, _, _, _) if k == name => qp
      }
      .map(_.v)

  override def queryParameters: MultiQueryParams =
    MultiQueryParams.fromMultiMap(
      r.uri.querySegments
        .collect {
          case qp @ QuerySegment.KeyValue(_, _, _, _) => qp
        }
        .groupBy(_.k)
        .map { case (k, vs) => (k, vs.map(_.v)) }
    )

  override def bodyStream: Any = r.body match {
    case StreamBody(s) => s
    case _             => throw new UnsupportedOperationException("Trying to read streaming body from a non-streaming request")
  }

  override def serverRequest: ServerRequest = new SttpStubServerRequest(r)
}
