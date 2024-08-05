package sttp.tapir.client.tests

import cats.effect._
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import cats.implicits._
import fs2.{Pipe, Stream}
import org.http4s.dsl.io._
import org.http4s.headers.{Accept, `Content-Type`}
import org.http4s.server.Router
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.middleware._
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.http4s._
import org.slf4j.LoggerFactory
import org.typelevel.ci.CIString
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

  private val logger = LoggerFactory.getLogger(getClass)

  private var stopServer: IO[Unit] = _

  //

  private object numParam extends QueryParamDecoderMatcher[Int]("num")
  private object fruitParam extends QueryParamDecoderMatcher[String]("fruit")
  private object amountOptParam extends OptionalQueryParamDecoderMatcher[String]("amount")
  private object colorOptParam extends OptionalQueryParamDecoderMatcher[String]("color")
  private object apiKeyOptParam extends OptionalQueryParamDecoderMatcher[String]("api-key")
  private object statusOutParam extends QueryParamDecoderMatcher[Int]("statusOut")

  private def service(wsb: WebSocketBuilder2[IO]) = HttpRoutes.of[IO] {
    case GET -> Root :? fruitParam(f) +& amountOptParam(amount) =>
      if (f == "papaya") {
        Accepted("29")
      } else if (f == "apricot") {
        Ok("30")
      } else {
        Ok(s"fruit: $f${amount.map(" " + _).getOrElse("")}", Header.Raw(CIString("X-Role"), f.length.toString))
      }
    case GET -> Root / "fruit" / f                                         => Ok(s"$f")
    case GET -> Root / "fruit" / f / "amount" / amount :? colorOptParam(c) => Ok(s"$f $amount $c")
    case _ @GET -> Root / "api" / "unit"                                   => Ok("{}")
    case r @ GET -> Root / "api" / "echo" / "params" => Ok(r.uri.query.params.toSeq.sortBy(_._1).map(p => s"${p._1}=${p._2}").mkString("&"))
    case r @ GET -> Root / "api" / "echo" / "headers" =>
      val headers = r.headers.headers.map(h => h.copy(value = h.value.reverse))
      val filteredHeaders1 = r.headers.headers.find(_.name == CIString("Cookie")) match {
        case Some(c) => headers.filter(_.name == CIString("Cookie")) :+ Header.Raw(CIString("Set-Cookie"), c.value.reverse)
        case None    => headers
      }

      val filteredHeaders2: Header.ToRaw = filteredHeaders1.filterNot(_.name == CIString("Content-Length"))
      okOnlyHeaders(List(filteredHeaders2))
    case r @ GET -> Root / "api" / "echo" / "param-to-header" =>
      okOnlyHeaders(r.uri.multiParams.getOrElse("qq", Nil).reverse.map("hh" -> _: Header.ToRaw))
    case r @ GET -> Root / "api" / "echo" / "param-to-upper-header" =>
      okOnlyHeaders(r.uri.multiParams.map { case (k, v) =>
        k -> v.headOption.getOrElse("?"): Header.ToRaw
      }.toSeq)
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
      r.headers.get(CIString("X-Role")) match {
        case None     => Ok()
        case Some(hs) => Ok("Role: " + hs.head.value)
      }

    case r @ GET -> Root / "secret" =>
      r.headers.get(CIString("Location")) match {
        case None     => BadRequest()
        case Some(hs) => Ok("Location: " + hs.head.value)
      }

    case DELETE -> Root / "api" / "delete" => Ok()

    case r @ GET -> Root / "auth" :? apiKeyOptParam(ak) :? amountOptParam(am) =>
      val authHeader = r.headers.get(CIString("Authorization")).map(_.head.value)
      val xApiKey = r.headers.get(CIString("X-Api-Key")).map(_.head.value)
      Ok(s"Authorization=$authHeader; X-Api-Key=$xApiKey; ApiKeyParam=$ak; AmountParam=$am")

    case GET -> Root / "mapping" :? numParam(v) =>
      if (v % 2 == 0) Accepted("A") else Ok("B")

    case _ @GET -> Root / "status" :? statusOutParam(status) =>
      status match {
        case 204 => NoContent()
        case 200 => Ok.headers(`Content-Type`(MediaType.text.plain))
        case _   => BadRequest()
      }

    case GET -> Root / "ws" / "echo" =>
      val echoReply: fs2.Pipe[IO, WebSocketFrame, WebSocketFrame] =
        _.collect { case WebSocketFrame.Text(msg, _) =>
          if (msg.contains("\"f\"")) {
            WebSocketFrame.Text(msg.replace("\"f\":\"", "\"f\":\"echo: ")) // json echo
          } else {
            WebSocketFrame.Text("echo: " + msg) // string echo
          }
        }

      Queue
        .unbounded[IO, WebSocketFrame]
        .flatMap { q =>
          val d = Stream.repeatEval(q.take).through(echoReply)
          val e: Pipe[IO, WebSocketFrame, Unit] = s => s.evalMap(q.offer)
          wsb.build(d, e)
        }

    case GET -> Root / "ws" / "echo" / "fragmented" =>
      val echoReply: fs2.Pipe[IO, WebSocketFrame, WebSocketFrame] =
        _.flatMap {
          case WebSocketFrame.Text(msg, _) =>
            fs2.Stream(
              WebSocketFrame.Text(s"fragmented frame with ", last = false),
              WebSocketFrame.Continuation(ByteVector.view(s"echo: $msg".getBytes()), last = true),
              WebSocketFrame.Close()
            )
          case f => throw new IllegalArgumentException(s"Unsupported frame: $f")
        }

      Queue
        .unbounded[IO, WebSocketFrame]
        .flatMap { q =>
          val d = Stream.repeatEval(q.take).through(echoReply)
          val e: Pipe[IO, WebSocketFrame, Unit] = s => s.evalMap(q.offer)
          wsb.build(d, e)
        }

    case GET -> Root / "ws" / "echo" / "header" =>
      val echoReply: fs2.Pipe[IO, WebSocketFrame, WebSocketFrame] =
        _.collect { case WebSocketFrame.Text(msg, _) => WebSocketFrame.Text("echo: " + msg) }

      Queue
        .unbounded[IO, WebSocketFrame]
        .flatMap { q =>
          val d = Stream.repeatEval(q.take).through(echoReply)
          val e: Pipe[IO, WebSocketFrame, Unit] = s => s.evalMap(q.offer)
          wsb
            .withHeaders(Headers(Header.Raw(CIString("Correlation-id"), "ABC-DEF-123")))
            .build(d, e)
        }

    case GET -> Root / "entity" / entityType =>
      if (entityType == "person") Created("""{"name":"mary","age":20}""")
      else Ok("""{"name":"work"}""")

    case GET -> Root / "one-of" :? fruitParam(f) =>
      if (f == "apple") BadRequest("""{"name":"apple"}""", jsonContentType)
      else if (f == "pear") BadRequest("""{"availableInDays":10}""", jsonContentType)
      else if (f.length < 3) BadRequest(s"""{"length":${f.length}""", jsonContentType)
      else if (f == "orange") Ok()
      else NotFound("""{"availableFruit":["orange"]}""", jsonContentType)

    case r @ GET -> Root / "content-negotiation" / "organization" =>
      fromAcceptHeader(r) {
        case "application/json" => organizationJson
        case "application/xml"  => organizationXml
      }

    case r @ GET -> Root / "content-negotiation" / "entity" =>
      fromAcceptHeader(r) {
        case "application/json" => personJson
        case "application/xml"  => organizationXml
      }

    case r @ POST -> Root / "content-negotiation" / "fruit" =>
      r.as[String].flatMap { body =>
        fromAcceptHeader(r) {
          case "application/json" => Ok(s"""{"f": "$body (json)"}""", `Content-Type`(MediaType.application.json, Charset.`UTF-8`))
          case "application/xml"  => Ok(s"<f>$body (xml)</f>", `Content-Type`(MediaType.application.xml, Charset.`UTF-8`))
        }
      }
  }

  private def okOnlyHeaders(headers: Seq[Header.ToRaw]): IO[Response[IO]] = IO.pure(Response(headers = Headers(headers)))

  private def fromAcceptHeader(r: Request[IO])(f: PartialFunction[String, IO[Response[IO]]]): IO[Response[IO]] =
    r.headers.get[Accept].map(h => f(h.values.head.toString())).getOrElse(NotAcceptable())

  private val jsonContentType = `Content-Type`(MediaType.application.json, Charset.`UTF-8`)
  private val organizationXml = Ok("<name>sml-xml</name>", `Content-Type`(MediaType.application.xml, Charset.`UTF-8`))
  private val organizationJson = Ok("{\"name\": \"sml\"}", jsonContentType)
  private val personJson = Ok("{\"name\": \"John\", \"age\": 21}", jsonContentType)

  private def app(wsb: WebSocketBuilder2[IO]): HttpApp[IO] = {
    val corsService = CORS(service(wsb))
    Router("/" -> corsService).orNotFound
  }

  //

  def start(): Unit = {
    val (_, _stopServer) = BlazeServerBuilder[IO]
      .withExecutionContext(ExecutionContext.global)
      .bindHttp(port)
      .withHttpWebSocketApp(app)
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
