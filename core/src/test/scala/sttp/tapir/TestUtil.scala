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

  type Id[X] = X

  implicit val idMonadError: MonadError[Id] = new MonadError[Id] {
    override def unit[T](t: T): Id[T] = t
    override def map[T, T2](fa: Id[T])(f: T => T2): Id[T2] = f(fa)
    override def flatMap[T, T2](fa: Id[T])(f: T => Id[T2]): Id[T2] = f(fa)
    override def error[T](t: Throwable): Id[T] = throw t
    override protected def handleWrappedError[T](rt: Id[T])(h: PartialFunction[Throwable, Id[T]]): Id[T] = rt
    override def ensure[T](f: Id[T], e: => Id[Unit]): Id[T] = try f
    finally e
  }

  case class PersonsApi(logic: String => Id[Either[String, String]] = PersonsApi.defaultLogic) {
    def serverEp: ServerEndpoint[Any, Id] = endpoint
      .in("person")
      .in(query[String]("name"))
      .out(stringBody)
      .errorOut(stringBody)
      .serverLogic(logic)
  }

  object PersonsApi {
    val defaultLogic: String => Id[Either[String, String]] = name => (if (name == "Jacob") Right("hello") else Left(":(")).unit

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
