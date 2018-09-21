package com.softwaremill.swagger

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.softwaremill.sttp._
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import shapeless.ops.hlist.Prepend
import shapeless._
import shapeless.ops.function
import shapeless.ops.function.FnToProduct

import scala.annotation.implicitNotFound
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object Main extends App {
  /*
  Goals:
  - user-friendly types (also in idea); as simple as possible to generate the client, server & docs
  - Swagger-first
  - reasonably type-safe: only as much as needed to gen a server/client/docs, no more
  - programmer friendly (ctrl-space)
   */

  /*
  Akka http directives:
  - authenticate basic/oauth, authorize (fn)
  - cache responses
  - complete (with)
  - decompress request with
  - add/remove cookie
  - extract headers
  - extract body: entity, form field; save to file
  - method matchers
  - complete with file/directory
  - transform request or response (fn)
  - extract parameters
  - match path (extract suffix, ignore trailing slash)
  - redirects
   */

  // define model using case classes
  // capture path components and their mapping to parameters
  // capture query, body, cookie, header parameters w/ mappings
  // read a yaml to get the model / auto-generate the model from a yaml ?
  //   -> only generation possible, due to type-safety
  //   -> the scala model is richer, as it has all the types + case classes
  // server: generate an http4s/akka endpoint matcher
  // client: generate an sttp request definition

  // separate logic from endpoint definition & documentation

  // provide as much or as little detail as needed: optional query param/endpoint desc, samples
  // reasonably type-safe

  // https://github.com/felixbr/swagger-blocks-scala
  // https://typelevel.org/blog/2018/06/15/typedapi.html (https://github.com/pheymann/typedapi)
  // http://fintrospect.io/defining-routes
  // https://github.com/http4s/rho
  // https://github.com/TinkoffCreditSystems/typed-schema

  // what to capture: path, query parameters, body, headers, default response body, error response body

  // streaming?

  // type: string, format: base64, binary, email, ... - use tagged string types ?
  // type: object                                     - implicit EndpointInputType values
  // form fields, multipart uploads, ...

  // extend the path for an endpoint?

  //

  type Id[X] = X
  type Empty[X] = None.type

  @implicitNotFound("???")
  type IsId[U[_]] = U[Unit] =:= Id[Unit]

  sealed trait Segment[S <: HList]
  case class StringSegment(s: String) extends Segment[HNil]
  case class CaptureSegment[S]() extends Segment[S :: HNil]
  def segment[S]: Segment[S :: HNil] = CaptureSegment[S]()

  case class Path[P <: HList](segments: Vector[Segment[_]]) {
    def /(s: String): Path[P] = /(StringSegment(s))
    def /[S <: HList, PS <: HList](s: Segment[S])(implicit ts: Prepend.Aux[P, S, PS]): Path[PS] = Path(segments :+ s)
  }
  val Root = Path[HNil](Vector.empty)

  trait LogicToServer[P <: HList, F[_], S] {
    def using[I](logic: I)(implicit tt: FnToProduct.Aux[I, P => F[String]]): S
  }

  trait HostToClient[P <: HList, R[_]] {
    def using[O](host: String)(implicit tt: function.FnFromProduct.Aux[P => R[String], O]): O
  }

  case class Endpoint[U[_], P <: HList](name: Option[String], method: U[Method], path: U[Path[P]]) {
    def name(s: String): Endpoint[U, P] = this.copy(name = Some(s))
    def get[PP <: HList](p: Path[PP]): Endpoint[Id, PP] = this.copy[Id, PP](path = p, method = Method.GET)

    def toServer[F[_], S](implicit ets: EndpointToServer[F, S], isId: IsId[U]): LogicToServer[P, F, S] =
      ets.toServer(this.asInstanceOf[Endpoint[Id, P]])

    def toClient[R[_]](implicit etc: EndpointToClient[R], isId: IsId[U]): HostToClient[P, R] =
      etc.toClient(this.asInstanceOf[Endpoint[Id, P]])
  }

  //

  val endpoint = Endpoint[Empty, HNil](None, None, None)

// TODO
//  case class User()
//
//  implicit val userType = json[User].sample(User("x"))
//  implicit val ? = string.format(base64)
//  implicit val productErrorType = json[ProductError]
//
//  val qp = query[String]("v").description("p")      // : EndpointInput[HList] - each input extracts 0+ data from the request
//  // an input can be created from a function RequestContext => T
//  val hp = header("X-Y")
//  val bp = body[User]
//
//  // verify that there's only one body + that query params aren't repeated?
//  val inputs = qp.and(hp).and(bp) // andUsing(combineFn)

  // sapi ?

  val p = Root / "x" / segment[String] / "z"

  val e = endpoint.get(Root / "x" / segment[String] / "z" / segment[String]) // each endpoint must have a path and a method

// TODO
//    .in(query[Int]("x"))
//    .in(inputs)
//    .in(header("Cookie"))
//    .out[String]
//    .outError[ProductError] // for 4xx status codes
//    .name("xz") // name optional
//    .description("...")

  trait EndpointToServer[F[_], S] {
    def toServer[P <: HList](e: Endpoint[Id, P]): LogicToServer[P, F, S]
  }

  class EndpointToAkkaServer extends EndpointToServer[Future, Route] {
    def toServer[P <: HList](e: Endpoint[Id, P]): LogicToServer[P, Future, Route] = new LogicToServer[P, Future, Route] {

      import akka.http.scaladsl.server._
      import akka.http.scaladsl.server.Directives._

      val methodDirective = e.method match {
        case Method.GET => get
        case _          => post
      }

      def doMatch(segments: Vector[Segment[_]], path: Uri.Path, canRemoveSlash: Boolean): Option[(Vector[Any], Uri.Path)] = {
        segments match {
          case Vector() => Some((Vector.empty, path))
          case StringSegment(ss) +: segmentsTail =>
            path match {
              case Uri.Path.Slash(pathTail) if canRemoveSlash => doMatch(segments, pathTail, canRemoveSlash = false)
              case Uri.Path.Segment(`ss`, pathTail)           => doMatch(segmentsTail, pathTail, canRemoveSlash = true)
              case _                                          => None
            }
          case CaptureSegment() +: segmentsTail =>
            path match {
              case Uri.Path.Slash(pathTail) if canRemoveSlash => doMatch(segments, pathTail, canRemoveSlash = false)
              case Uri.Path.Segment(s, pathTail) =>
                doMatch(segmentsTail, pathTail, canRemoveSlash = true).map {
                  case (values, remainingPath) =>
                    (s +: values, remainingPath)
                }
              case _ => None
            }
        }
      }

      val pathDirectives: Directive1[Vector[Any]] = extractRequestContext.flatMap { ctx =>
        doMatch(e.path.segments, ctx.request.uri.path, canRemoveSlash = true) match {
          case Some((values, remainingPath)) =>
            provide(values: Vector[Any]) & mapRequestContext(_.withUnmatchedPath(remainingPath))
          case None => reject
        }
      }

      override def using[I](logic: I)(implicit tt: function.FnToProduct.Aux[I, P => Future[String]]): Route = {
        (methodDirective & pathDirectives) { values =>
          val params = values.foldRight(HNil: HList) {
            case (el, hlist) =>
              el :: hlist
          }

          onComplete(tt(logic)(params.asInstanceOf[P])) { x =>
            complete(x)
          }
        }
      }
    }
  }

  implicit val ets = new EndpointToAkkaServer

  val r: Route = e.toServer.using((i: String, s: String) => Future.successful(s"$i $s"))

  //

  trait EndpointToClient[R[_]] {
    def toClient[P <: HList](e: Endpoint[Id, P]): HostToClient[P, R]
  }

  class EndpointToSttpClient extends EndpointToClient[Request[?, Nothing]] {
    override def toClient[P <: HList](e: Endpoint[Id, P]): HostToClient[P, Request[?, Nothing]] = {
      new HostToClient[P, Request[?, Nothing]] {
        override def using[O](host: String)(implicit tt: function.FnFromProduct.Aux[P => Request[String, Nothing], O]): O = {
          tt(args => {
            var i = -1
            val path = e.path.segments.map {
              case StringSegment(s) => s
              case CaptureSegment() =>
                i += 1
                HList.unsafeGet(args, i).asInstanceOf[String]
            }

            e.method match {
              case com.softwaremill.swagger.Method.GET =>
                sttp.get(uri"${host + "/" + path.mkString("/")}")
            }
          })
        }
      }
    }
  }

  // TEST

  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()
  val server = Await.result(Http().bindAndHandle(r, "localhost", 8080), 1.minute)

  import com.softwaremill.sttp._
  implicit val backend = AkkaHttpBackend.usingActorSystem(actorSystem)

  val response = Await.result(sttp.get(uri"http://localhost:8080/x/10/z/aaa").send(), 1.minute)
  println("RESPONSE1: " + response)

  type SttpReq[T] = Request[T, Nothing] // needed, unless implicit error
  implicit val etc: EndpointToClient[SttpReq] = new EndpointToSttpClient
  val response2 = Await.result(e.toClient.using("http://localhost:8080").apply("11", "bbb").send(), 1.minute)
  println("RESPONSE2: " + response2)

  Await.result(actorSystem.terminate(), 1.minute)
}
