package sttp.tapir.grpc.examples

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import cats.implicits._
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}
import sttp.tapir._
import sttp.tapir.grpc.protobuf._
import sttp.tapir.grpc.protobuf.pbdirect._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkagrpc.AkkaGrpcServerInterpreter
import sttp.tapir.generic.auto._
import sttp.tapir.grpc.examples.grpc_simple_books_example.gen.{
  Library => GenLibrary,
  LibraryClient => GenLibraryClient,
  AddBookMsg => GenAddBookMsg
}

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.{Await, ExecutionContext, Future}

trait Logging {
  protected val logger: Logger = LoggerFactory.getLogger(getClass.getName)
}
case class SimpleBook(id: Long, title: String, description: String)
case class AddBookMsg(title: String, description: String)

/** Descriptions of endpoints used in the example.
  */
object Endpoints {
  val addBook = endpoint
    .in("Library" / "AddBook")
    .in(grpcBody[AddBookMsg])
    .out(grpcBody[SimpleBook])

  val endpoints = List(addBook)
}

object SimpleBooksExampleServer extends Logging {

  import Endpoints._

  private val counter = new AtomicLong(0)

  def booksServerEndpoints: List[ServerEndpoint[Any, Future]] =
    List(
      addBook.serverLogic { book =>
        logger.info(s"Adding a new book [$book]")
        Future.successful(SimpleBook(counter.getAndIncrement(), book.title, book.description).asRight[Unit])
      }
    )

  def main(args: Array[String]): Unit = {
    val conf = ConfigFactory
      .parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    val system = ActorSystem("HelloWorld", conf)

    new ExampleGrpcServer(system).run()
  }
}

class ExampleGrpcServer(system: ActorSystem) extends Logging {
  def run(): Future[Http.ServerBinding] = {
    // Akka boot up code
    implicit val sys: ActorSystem = system
    implicit val ec: ExecutionContext = sys.dispatcher

    val route = AkkaGrpcServerInterpreter().toRoute(SimpleBooksExampleServer.booksServerEndpoints)

    val binding = Http().newServerAt("127.0.0.1", 8080).bind(route)

    // report successful binding
    binding.foreach { binding => logger.info(s"gRPC server bound to: ${binding.localAddress}") }

    binding
  }
}

object SimpleBookExampleProtoGenerator extends App {
  ProtoSchemaGenerator.renderToFile(
    path = "grpc/examples/src/main/protobuf/simple_books_example.proto",
    packageName = "sttp.tapir.grpc.examples.grpc_simple_books_example.gen",
    endpoints = Endpoints.endpoints
  )
}

object SimpleBookExampleClient extends App with Logging {

  import scala.concurrent.duration._

  implicit val sys = ActorSystem("HelloWorldClient")
  implicit val ec = sys.dispatcher

  val client = GenLibraryClient(GrpcClientSettings.connectToServiceAt("localhost", 8080).withTls(false))
  val result = Await.result(client.addBook(GenAddBookMsg("TEST_BOOK", "TEST")), 10.second)

  logger.info(s"Result: [$result]")
}
