package sttp.tapir

import sttp.model.Uri._
import sttp.model._
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.SchemaType.SProductField
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.server.ServerEndpoint

import scala.collection.immutable

object TestUtil {
  def field[T, U](_name: FieldName, _schema: Schema[U]): SchemaType.SProductField[T] = SProductField[T, U](_name, _schema, _ => None)

  implicit val idMonad: MonadError[Identity] = sttp.tapir.internal.idMonad

  case class PersonsApi(logic: String => Identity[Either[String, String]] = PersonsApi.defaultLogic) {
    def serverEp: ServerEndpoint[Any, Identity] = endpoint
      .in("person")
      .in(query[String]("name"))
      .out(stringBody)
      .errorOut(stringBody)
      .serverLogic(logic)
  }

  object PersonsApi {
    val defaultLogic: String => Identity[Either[String, String]] = name => (if (name == "Jacob") Right("hello") else Left(":(")).unit

    val request: String => ServerRequest = name => {
      new ServerRequest {
        override def protocol: String = ""
        override def connectionInfo: ConnectionInfo = ConnectionInfo(None, None, None)
        override def underlying: Any = ()
        override def pathSegments: List[String] = List("person")
        override def queryParameters: QueryParams = if (name == "") QueryParams.apply() else QueryParams.fromSeq(Seq(("name", name)))
        override def method: Method = Method.GET
        override def uri: Uri = uri"http://example.com/person"
        override def headers: immutable.Seq[Header] = Nil
        override def attribute[T](k: AttributeKey[T]): Option[T] = None
        override def attribute[T](k: AttributeKey[T], v: T): ServerRequest = this
        override def withUnderlying(underlying: Any): ServerRequest = this
      }
    }
  }
}
