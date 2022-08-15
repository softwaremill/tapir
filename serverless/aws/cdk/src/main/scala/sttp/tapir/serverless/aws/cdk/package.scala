package sttp.tapir.serverless.aws

import cats.effect.kernel.Sync
import cats.implicits._
import sttp.model.Method
import sttp.tapir.EndpointInput.{FixedMethod, FixedPath, PathCapture}
import sttp.tapir._
import sttp.tapir.internal.RichEndpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda.{AwsRequest, AwsRequestV1}

package object cdk {

  type Nodes = List[Node]

  // service urls are structured as tree
  case class Node(
      name: Segment,
      methods: List[String] = List.empty[String],
      content: List[Node] = List.empty[Node]
  )

  trait Upcaster[A] {
    def toV2(a: A): AwsRequest
  }

  implicit val upcasterV1: Upcaster[AwsRequestV1] = (a: AwsRequestV1) => a.toV2

  sealed trait Segment {
    def toString: String
  }

  case class Fixed(value: String) extends Segment {
    override def toString: String = value
  }

  case class Parameter(value: String) extends Segment {
    override def toString: String = s"{$value}"
  }

  object Segment {
    def apply(value: String): Segment = {
      "^\\{(.+)\\}$".r.findFirstMatchIn(value) match {
        case None     => Fixed(value)
        case Some(xx) => Parameter(xx.group(1))
      }
    }
  }

  case class Url(method: String, path: List[Segment]) {
    def addPath(segment: Segment): Url =
      this.copy(path = path :+ segment)

    def getPath: List[Segment] = path

    def withMethod(m: Method): Url = this.copy(method = m.toString())

    // todo: add smart constructor to return ANY method by default
  }

  // fixme: move to core module?
  implicit final class EndpointOps(private val endpoint: AnyEndpoint) {
    def getPath: List[Segment] = endpoint.toUrl.getPath

    def getMethod: String = endpoint.toUrl.method // fixme use method value object?

    // fixme: this logic is duplicated in other serverless modules
    def toUrl: Url = endpoint
      .asVectorOfBasicInputs()
      .foldLeft((Url("", List.empty[Segment]), 0)) { case ((url, count), input) =>
        input match {
          case FixedMethod(m, _, _) => (url.withMethod(m), count)
          case FixedPath(s, _, _)   => (url.addPath(Fixed(s)), count)
          case PathCapture(n, _, _) => (url.addPath(Parameter(n.getOrElse(s"param$count"))), count + 1)
          case _                    => (url, count)
        }
      }
      ._1
  }

  // fixme: rename
  implicit final class BetterName(private val input: List[String]) {
    def toSegments: List[Segment] = input.map(Segment(_))
  }

  // fixme: rename
  implicit final class BetterName2(private val input: List[Segment]) {
    // fixme: why can not name it toString()?
    def stringify: String = input.map(_.toString).mkString("/")
  }

  // fixme is this good idea to keep those endpoints here?
  def hello[F[_]: Sync]: ServerEndpoint[Any, F] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody).serverLogic { case (name: String) =>
      Sync[F].pure(s"Hi! $name".asRight[Unit])
    }

  def hello_with_id[F[_]: Sync]: ServerEndpoint[Any, F] =
    endpoint.get.in("hello" / path[Int]("id")).out(stringBody).serverLogic { case (id: Int) =>
      Sync[F].pure(s"Hello from the other side id: $id".asRight[Unit])
    }

  def allEndpoints[F[_]: Sync]: Set[ServerEndpoint[Any, F]] = Set(
    hello[F],
    hello_with_id[F]
  )
}
