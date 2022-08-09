package sttp.tapir.grpc.examples

import akka.actor.ActorSystem
import akka.grpc.ProtobufSerializer
import akka.http.scaladsl.Http
import akka.util.ByteString
import cats.implicits._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import sttp.tapir.DecodeResult.Value
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.grpc.protobuf._
import sttp.tapir.grpc.protobuf.model._
import sttp.tapir.grpc.protobuf.pbdirect._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.concurrent.Future

case class SimpleBook(title: String)

/** Descriptions of endpoints used in the example.
  */
object Endpoints {

  private val baseEndpoint = endpoint.errorOut(stringBody)

  val addBook = endpoint
    .in("Library" / "AddBook")
    .in(grpcBody[SimpleBook])
    .out(grpcBody[SimpleBook])

  val proto = new ProtobufInterpreter(new EndpointToProtobufMessage(), new EndpointToProtobufService()).toProtobuf(List(addBook))
}

object SimpleBooksExample extends StrictLogging {

  import Endpoints._

  def booksServerEndpoints: List[ServerEndpoint[Any, Future]] =
    List(
      addBook.serverLogic { book =>
        println(book)
        Future.successful(book.asRight[Unit])
      }
    )

  def main(args: Array[String]): Unit = {
    val conf = ConfigFactory
      .parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    val system = ActorSystem("HelloWorld", conf)

    new TestServer(system).run()
  }
}

class TestServer(system: ActorSystem) {
  import akka.actor.{ActorSystem, ClassicActorSystemProvider}
  import akka.grpc.internal.TelemetryExtension
  import akka.grpc.scaladsl._
  import akka.http.scaladsl.model
  import akka.stream.SystemMaterializer

  import scala.concurrent.ExecutionContext

  def route()(implicit
      system: ClassicActorSystemProvider
  ): PartialFunction[model.HttpRequest, scala.concurrent.Future[model.HttpResponse]] = {
    implicit val mat = SystemMaterializer(system).materializer
    implicit val ec: ExecutionContext = mat.executionContext
    val spi = TelemetryExtension(system).spi
    val eHandler = GrpcExceptionHandler.defaultMapper _
    val prefix = "Library"

    val simpleBookSerializer = new ProtobufSerializer[SimpleBook] {

      override def serialize(t: SimpleBook): ByteString = {
        val encoded = implicitly[Codec[Array[Byte], SimpleBook, CodecFormat.OctetStream]].encode(t)
        ByteString.apply(encoded)
      }

      override def deserialize(bytes: ByteString): SimpleBook = {
        implicitly[Codec[Array[Byte], SimpleBook, CodecFormat.OctetStream]].decode(bytes.toArrayUnsafe()) match {
          case Value(v) => v
          case _        => ???
        }
      }

    }

    def handle(request: model.HttpRequest, method: String): scala.concurrent.Future[model.HttpResponse] =
      GrpcMarshalling
        .negotiated(
          request,
          (reader, writer) => {
            (method match {

              case "AddBook" =>
                GrpcMarshalling
                  .unmarshal(request.entity)(simpleBookSerializer, mat, reader)
                  .flatMap { x =>
                    println(s"RECEIVED: [$x]")
                    Future.successful(x)
                  }
                  .map(e => GrpcMarshalling.marshal(e, eHandler)(simpleBookSerializer, writer, system))

              case m => scala.concurrent.Future.failed(new NotImplementedError(s"Not implemented: $m"))
            })
              .recoverWith(GrpcExceptionHandler.from(eHandler(system.classicSystem))(system, writer))
          }
        )
        .getOrElse(scala.concurrent.Future.successful(model.HttpResponse(model.StatusCodes.UnsupportedMediaType)))

    Function.unlift { (req: model.HttpRequest) =>
      req.uri.path match {
        case model.Uri.Path
              .Slash(model.Uri.Path.Segment(`prefix`, model.Uri.Path.Slash(model.Uri.Path.Segment(method, model.Uri.Path.Empty)))) =>
          Some(handle(spi.onRequest(prefix, method, req), method))
        case _ =>
          None
      }
    }
  }

  def run(): Future[Http.ServerBinding] = {
    // Akka boot up code
    implicit val sys: ActorSystem = system
    implicit val ec: ExecutionContext = sys.dispatcher

     val route = AkkaHttpServerInterpreter().toRoute(SimpleBooksExample.booksServerEndpoints)

    // val binding = Http().newServerAt("127.0.0.1", 8080).bind(route)
    val binding = Http().newServerAt("127.0.0.1", 8080).bind(route())

    // report successful binding
    binding.foreach { binding => println(s"gRPC server bound to: ${binding.localAddress}") }

    binding
  }
}

object Main extends ProtoSchemaRegistry {
  val renderer: ProtoRenderer = new ProtoRenderer()
  // val path: String = "???/tapir/grpc/examples/src/main/protobuf/main.proto"
  val path: String = "/Users/mborek/OSS/tapir/grpc/examples/src/main/protobuf/main.proto"
  val proto: Protobuf = Endpoints.proto

  register()
}
