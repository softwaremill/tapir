package sttp.tapir.client.tests

import cats.effect._
import cats.implicits._
import org.http4s.dsl.io._
import org.http4s.headers.{Accept, `Content-Type`}
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware._
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.syntax.kleisli._
import org.http4s.util.CaseInsensitiveString
import org.http4s.websocket.WebSocketFrame
import org.http4s.{multipart, _}
import scodec.bits.ByteVector
import sttp.tapir.client.tests.HttpServer._

import scala.concurrent.ExecutionContext

object HttpServer {
  type Port = Int

  def main(args: Array[String]): Unit = {
    val port = args.headOption.map(_.toInt).getOrElse(51823)
    new HttpServer(port).start()
  }
}

class HttpServer(port: Port) {

  private val logger = org.log4s.getLogger

  implicit private val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit private val contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit private val timer: Timer[IO] = IO.timer(ec)

  private var stopServer: IO[Unit] = _

  //

  private object numParam extends QueryParamDecoderMatcher[Int]("num")
  private object fruitParam extends QueryParamDecoderMatcher[String]("fruit")
  private object amountOptParam extends OptionalQueryParamDecoderMatcher[String]("amount")
  private object colorOptParam extends OptionalQueryParamDecoderMatcher[String]("color")
  private object apiKeyOptParam extends OptionalQueryParamDecoderMatcher[String]("api-key")

  private val service = HttpRoutes.of[IO] {
    case GET -> Root :? fruitParam(f) +& amountOptParam(amount) =>
      if (f == "papaya") {
        Accepted("29")
      } else {
        Ok(s"fruit: $f${amount.map(" " + _).getOrElse("")}", Header("X-Role", f.length.toString))
      }
    case GET -> Root / "fruit" / f                                         => Ok(s"$f")
    case GET -> Root / "fruit" / f / "amount" / amount :? colorOptParam(c) => Ok(s"$f $amount $c")
    case _ @GET -> Root / "api" / "unit"                                   => Ok("{}")
    case r @ GET -> Root / "api" / "echo" / "params"                       => Ok(r.uri.query.params.toSeq.sortBy(_._1).map(p => s"${p._1}=${p._2}").mkString("&"))
    case r @ GET -> Root / "api" / "echo" / "headers" =>
      val headers = r.headers.toList.map(h => Header(h.name.value, h.value.reverse))
      val filteredHeaders = r.headers.find(_.name.value.equalsIgnoreCase("Cookie")) match {
        case Some(c) => headers.filter(_.name.value.equalsIgnoreCase("Cookie")) :+ Header("Set-Cookie", c.value.reverse)
        case None    => headers
      }
      Ok(headers = filteredHeaders: _*)
    case r @ GET -> Root / "api" / "echo" / "param-to-header" =>
      Ok(headers = r.uri.multiParams.getOrElse("qq", Nil).reverse.map(v => Header("hh", v)): _*)
    case r @ GET -> Root / "api" / "echo" / "param-to-upper-header" =>
      Ok(headers = r.uri.multiParams.map { case (k, v) => Header(k.toUpperCase(), v.headOption.getOrElse("?")) }.toSeq: _*)
    case r @ POST -> Root / "api" / "echo" / "multipart" =>
      r.decode[multipart.Multipart[IO]] { mp =>
        val parts: Vector[multipart.Part[IO]] = mp.parts
        def toString(s: fs2.Stream[IO, Byte]): IO[String] = s.through(fs2.text.utf8Decode).compile.foldMonoid
        def partToString(name: String): IO[String] = parts.find(_.name.contains(name)).map(p => toString(p.body)).getOrElse(IO.pure(""))
        partToString("fruit").product(partToString("amount")).flatMap { case (fruit, amount) =>
          Ok(s"$fruit=$amount")
        }
      }
    case r @ POST -> Root / "api" / "echo" => r.as[String].flatMap(Ok(_))
    case r @ GET -> Root =>
      r.headers.get(CaseInsensitiveString("X-Role")) match {
        case None    => Ok()
        case Some(h) => Ok("Role: " + h.value)
      }

    case r @ GET -> Root / "secret" =>
      r.headers.get(CaseInsensitiveString("Location")) match {
        case None    => BadRequest()
        case Some(h) => Ok("Location: " + h.value)
      }

    case DELETE -> Root / "api" / "delete" => Ok()

    case r @ GET -> Root / "auth" :? apiKeyOptParam(ak) =>
      val authHeader = r.headers.get(CaseInsensitiveString("Authorization")).map(_.value)
      val xApiKey = r.headers.get(CaseInsensitiveString("X-Api-Key")).map(_.value)
      Ok(s"Authorization=$authHeader; X-Api-Key=$xApiKey; Query=$ak")

    case GET -> Root / "mapping" :? numParam(v) =>
      if (v % 2 == 0) Accepted("A") else Ok("B")

    case GET -> Root / "ws" / "echo" =>
      val echoReply: fs2.Pipe[IO, WebSocketFrame, WebSocketFrame] =
        _.collect { case WebSocketFrame.Text(msg, _) =>
          if (msg.contains("\"f\"")) {
            WebSocketFrame.Text(msg.replace("\"f\":\"", "\"f\":\"echo: ")) // json echo
          } else {
            WebSocketFrame.Text("echo: " + msg) // string echo
          }
        }

      fs2.concurrent.Queue
        .unbounded[IO, WebSocketFrame]
        .flatMap { q =>
          val d = q.dequeue.through(echoReply)
          val e = q.enqueue
          WebSocketBuilder[IO].build(d, e)
        }

    case GET -> Root / "ws" / "echo" / "fragmented" =>
      val echoReply: fs2.Pipe[IO, WebSocketFrame, WebSocketFrame] =
        _.flatMap { case WebSocketFrame.Text(msg, _) =>
          fs2.Stream(
            WebSocketFrame.Text(s"fragmented frame with ", last = false),
            WebSocketFrame.Continuation(ByteVector.view(s"echo: $msg".getBytes()), last = true),
            WebSocketFrame.Close()
          )
        }

      fs2.concurrent.Queue
        .unbounded[IO, WebSocketFrame]
        .flatMap { q =>
          val d = q.dequeue.through(echoReply)
          val e = q.enqueue
          WebSocketBuilder[IO].build(d, e)
        }

    case r @ GET -> Root / "api" / "organization" =>
      r.headers
        .get(Accept)
        .map(_.value match {
          case "application/json" => Ok("{\"name\": \"sml\"}", `Content-Type`(MediaType.application.json))
          case "application/xml"  => Ok("<organization><name>sml</name></organization>", `Content-Type`(MediaType.application.xml))
        })
        .getOrElse(NotAcceptable())

    case r @ GET -> Root / "api" / "entity" =>
      r.headers
        .get(Accept)
        .map(_.value match {
          case "application/json" => Ok("{\"name\": \"John\", \"age\": 21}", `Content-Type`(MediaType.application.json))
          case "application/xml"  => Ok("<organization><name>sml</name></organization>", `Content-Type`(MediaType.application.xml))
        })
        .getOrElse(NotAcceptable())


  }

  private val corsService = CORS(service)
  private val app: HttpApp[IO] = Router("/" -> corsService).orNotFound

  //

  def start(): Unit = {
    val (_, _stopServer) = BlazeServerBuilder[IO](ec)
      .bindHttp(port)
      .withHttpApp(app)
      .resource
      .map(_.address.getPort)
      .allocated
      .unsafeRunSync()

    stopServer = _stopServer

    logger.info(s"Server on port $port started")
  }

  def close(): Unit = {
    stopServer.unsafeRunSync()
    logger.info(s"Server on port $port stopped")
  }
}
