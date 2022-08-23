package sttp.tapir.serverless.aws.cdk

import cats.implicits.toFunctorFilterOps

package object core {
  type Tree = List[Node]

  implicit final class ListStringOps(private val input: List[String]) {
    def toRequest(method: Method): Request =
      Request(method, input.map(Segment(_)).flattenOption)
  }

  implicit final class ListSegmentOps(private val input: List[Segment]) {
    def stringify: String = input.map(_.toString).mkString("/")
  }
}
