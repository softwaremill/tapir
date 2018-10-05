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

  sealed trait EndpointInput[I <: HList] {
    def and[J <: HList, IJ <: HList](other: EndpointInput[J])(implicit ts: Prepend.Aux[I, J, IJ]): EndpointInput[IJ]
    def /[J <: HList, IJ <: HList](other: EndpointInput[J])(implicit ts: Prepend.Aux[I, J, IJ]): EndpointInput[IJ] = and(other)
  }

  object EndpointInput {
    sealed trait Single[I <: HList] extends EndpointInput[I] {
      def and[J <: HList, IJ <: HList](other: EndpointInput[J])(implicit ts: Prepend.Aux[I, J, IJ]): EndpointInput[IJ] =
        other match {
          case s: Single[_]     => EndpointInput.Multiple(Vector(this, s))
          case Multiple(inputs) => EndpointInput.Multiple(this +: inputs)
        }
    }

    case class PathSegment(s: String) extends Path[HNil] with Single[HNil]
    case class PathCapture[T](m: TypeMapper[T]) extends Path[T :: HNil] with Single[T :: HNil]

    case class Query[T](name: String, m: TypeMapper[T]) extends Single[T :: HNil]

    case class Multiple[I <: HList](inputs: Vector[Single[_]]) extends EndpointInput[I] {
      override def and[J <: HList, IJ <: HList](other: EndpointInput[J])(implicit ts: Prepend.Aux[I, J, IJ]): EndpointInput.Multiple[IJ] =
        other match {
          case s: Single[_] => EndpointInput.Multiple(inputs :+ s)
          case Multiple(m)  => EndpointInput.Multiple(inputs ++ m)
        }
    }
  }

  def pathCapture[T: TypeMapper]: EndpointInput[T :: HNil] = EndpointInput.PathCapture(implicitly[TypeMapper[T]])
  implicit def stringToPath(s: String): EndpointInput[HNil] = EndpointInput.PathSegment(s)

  def query[T: TypeMapper](name: String): EndpointInput[T :: HNil] = EndpointInput.Query(name, implicitly[TypeMapper[T]])

  case class Endpoint[U[_], I <: HList](name: Option[String], method: U[Method], input: EndpointInput.Multiple[I]) {
    def name(s: String): Endpoint[U, I] = this.copy(name = Some(s))

    def get(): Endpoint[Id, I] = this.copy[Id, I](method = Method.GET)

    def in[J <: HList, IJ <: HList](i: EndpointInput[J])(implicit ts: Prepend.Aux[I, J, IJ]): Endpoint[U, IJ] =
      this.copy[U, IJ](input = input.and(i))

    def toServer[R[_], S](implicit ets: EndpointToServer[R, S], isId: IsId[U]): LogicToServer[I, R, S] =
      ets.toServer(this.asInstanceOf[Endpoint[Id, I]])

    def toClient[R[_]](implicit etc: EndpointToClient[R], isId: IsId[U]): HostToClient[I, R] =
      etc.toClient(this.asInstanceOf[Endpoint[Id, I]])
  }

  trait EndpointToServer[R[_], S] {
    def toServer[I <: HList](e: Endpoint[Id, I]): LogicToServer[I, R, S]
  }

  trait EndpointToClient[R[_]] {
    def toClient[I <: HList](e: Endpoint[Id, I]): HostToClient[I, R]
  }

  trait LogicToServer[I <: HList, R[_], S] {
    def using[F](logic: F)(implicit tt: FnToProduct.Aux[F, I => R[String]]): S
  }

  trait HostToClient[I <: HList, R[_]] {
    def using[F](host: String)(implicit tt: function.FnFromProduct.Aux[I => R[String], F]): F
  }

  //

  val endpoint = Endpoint[Empty, HNil](None, None, EndpointInput.Multiple(Vector.empty))

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

//  def json[T]: TypeMapper[T]
//
//  case class User(name: String, age: Int)
//  implicit val userType: TypeMapper[User] = json[User]  // TODO.sample(User("x"))

  val path = "x" / pathCapture[String] / "z"

  val e = endpoint
    .get()
    .in("x" / pathCapture[String] / "z" / pathCapture[Int]) // each endpoint must have a path and a method
    .in(query[String]("q1").and(query[Int]("q2")))

// TODO
//    .in(query[Int]("x"))
//    .in(inputs)
//    .in(header("Cookie"))
//    .out[String]
//    .outError[ProductError] // for 4xx status codes
//    .name("xz") // name optional
//    .description("...")

  class EndpointToAkkaServer extends EndpointToServer[Future, Route] {
    def toServer[I <: HList](e: Endpoint[Id, I]): LogicToServer[I, Future, Route] = new LogicToServer[I, Future, Route] {

      import akka.http.scaladsl.server._
      import akka.http.scaladsl.server.Directives._

      val methodDirective = e.method match {
        case Method.GET => get
        case _          => post
      }

      def doMatch(inputs: Vector[EndpointInput.Single[_]],
                  ctx: RequestContext,
                  canRemoveSlash: Boolean): Option[(Vector[Any], RequestContext)] = {
        inputs match {
          case Vector() => Some((Vector.empty, ctx))
          case EndpointInput.PathSegment(ss) +: inputsTail =>
            ctx.unmatchedPath match {
              case Uri.Path.Slash(pathTail) if canRemoveSlash => doMatch(inputs, ctx.withUnmatchedPath(pathTail), canRemoveSlash = false)
              case Uri.Path.Segment(`ss`, pathTail)           => doMatch(inputsTail, ctx.withUnmatchedPath(pathTail), canRemoveSlash = true)
              case _                                          => None
            }
          case EndpointInput.PathCapture(m) +: inputsTail =>
            ctx.unmatchedPath match {
              case Uri.Path.Slash(pathTail) if canRemoveSlash => doMatch(inputs, ctx.withUnmatchedPath(pathTail), canRemoveSlash = false)
              case Uri.Path.Segment(s, pathTail) =>
                m.fromString(s) match {
                  case Some(v) =>
                    doMatch(inputsTail, ctx.withUnmatchedPath(pathTail), canRemoveSlash = true).map {
                      case (values, ctx2) => (v +: values, ctx2)
                    }
                  case None => None
                }
              case _ => None
            }
          case EndpointInput.Query(name, m) +: inputsTail =>
            ctx.request.uri.query().get(name).flatMap(m.fromString) match {
              case None => None
              case Some(value) =>
                doMatch(inputsTail, ctx, canRemoveSlash = true).map {
                  case (values, ctx2) => (value +: values, ctx2)
                }
            }
        }
      }

      val inputDirectives: Directive1[Vector[Any]] = extractRequestContext.flatMap { ctx =>
        doMatch(e.input.inputs, ctx, canRemoveSlash = true) match {
          case Some((values, ctx2)) => provide(values: Vector[Any]) & mapRequestContext(_ => ctx2)
          case None                 => reject
        }
      }

      override def using[F](logic: F)(implicit tt: function.FnToProduct.Aux[F, I => Future[String]]): Route = {
        (methodDirective & inputDirectives) { values =>
          val params = values.foldRight(HNil: HList) {
            case (el, hlist) =>
              el :: hlist
          }

          onComplete(tt(logic)(params.asInstanceOf[I])) { x =>
            complete(x)
          }
        }
      }
    }
  }

  implicit val ets = new EndpointToAkkaServer

  val r: Route = e.toServer.using((i: String, s: Int, p1: String, p2: Int) => Future.successful(s"$i $s $p1 $p2"))

  //

  class EndpointToSttpClient extends EndpointToClient[Request[?, Nothing]] {
    override def toClient[I <: HList](e: Endpoint[Id, I]): HostToClient[I, Request[?, Nothing]] = {
      new HostToClient[I, Request[?, Nothing]] {
        override def using[F](host: String)(implicit tt: function.FnFromProduct.Aux[I => Request[String, Nothing], F]): F = {
          tt(args => {
            var uri = uri"$host"
            var i = -1
            e.input.inputs.foreach {
              case EndpointInput.PathSegment(p) =>
                uri = uri.copy(path = uri.path :+ p)
              case EndpointInput.PathCapture(m) =>
                i += 1
                val v = m.toString(HList.unsafeGet(args, i))
                uri = uri.copy(path = uri.path :+ v)
              case EndpointInput.Query(name, m) =>
                i += 1
                val v = m.toString(HList.unsafeGet(args, i))
                uri = uri.param(name, v)
            }

            e.method match {
              case com.softwaremill.swagger.Method.GET => sttp.get(uri)
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
  val response2 = Await.result(e.toClient.using("http://localhost:8080").apply("aa", 20, "x1", 91).send(), 1.minute)
  println("RESPONSE2: " + response2)

  Await.result(actorSystem.terminate(), 1.minute)

  // types, that you are not afraid to write down
}
