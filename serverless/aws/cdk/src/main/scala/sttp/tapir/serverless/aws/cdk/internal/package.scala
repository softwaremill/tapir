package sttp.tapir.serverless.aws.cdk

package object internal {
  type Tree = List[Node]

  implicit final class ListStringOps(private val input: List[String]) {
    def toRequest(method: Method): Request = Request(method, input.flatMap(Segment.apply))
  }

  implicit final class ListSegmentOps(private val input: List[Segment]) {
    def stringify: String = input.map(_.toString).mkString("/")
  }
}
