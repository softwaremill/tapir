package sttp.tapir.examples

import sttp.tapir.generic.auto._
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.server.akkahttp.AkkaHttpServerOptions
import akka.actor.ActorSystem
import scala.concurrent.Future
import sttp.tapir.server.ServerEndpoint
import cats.implicits._

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.Http
import com.typesafe.config.ConfigFactory
import sttp.tapir.grpc.protobuf.pbdirect._
import sttp.tapir.grpc.protobuf.model._
import sttp.tapir.grpc.protobuf._
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

case class SimpleBook(title: String)

/** Descriptions of endpoints used in the example.
  */
object Endpoints {
  import sttp.tapir._

  private val baseEndpoint = endpoint.errorOut(stringBody).in("Library")

  val addBook = endpoint
    .in("AddBook")
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
  def run(): Future[Http.ServerBinding] = {
    // Akka boot up code
    implicit val sys: ActorSystem = system
    implicit val ec: ExecutionContext = sys.dispatcher

    val route = AkkaHttpServerInterpreter().toRoute(BooksExample.booksServerEndpoints)


    val binding = Http().newServerAt("127.0.0.1", 8080).bind(route)

    // report successful binding
    binding.foreach { binding => println(s"gRPC server bound to: ${binding.localAddress}") }

    binding
  }
}

object Main extends ProtoSchemaRegistry {
  val renderer: ProtoRenderer = new ProtoRenderer()
  val path: String = "../../../examples/src/main/protobuf/main.proto"
  val proto: Protobuf = Endpoints.proto

  register()
}
