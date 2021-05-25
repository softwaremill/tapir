package sttp.tapir.serverless.aws.terraform

import sttp.model.Method
import sttp.tapir.serverless.aws.terraform.EndpointsToTerraformConfig.IdMethodEndpointInput
import sttp.tapir.{Codec, EndpointIO, EndpointInput}

import java.util.UUID

private[terraform] object ApiResourceTree {
  val RootPathComponent: PathComponent =
    PathComponent("root", Method("ANY"), Left(EndpointInput.FixedPath("/", Codec.idPlain(), EndpointIO.Info.empty)))

  private val pathMatches: (PathComponent, PathComponent) => Boolean = (a, b) => {
    (a.component, b.component) match {
      case (Left(fp1), Left(fp2))   => fp1.s == fp2.s && a.method == b.method
      case (Right(pc1), Right(pc2)) => pc1.name == pc2.name && a.method == b.method
      case _                        => false
    }
  }

  def apply(basicInputs: Seq[Vector[IdMethodEndpointInput]]): ResourceTree = {

    val endpointPathComponents: Seq[Seq[PathComponent]] = basicInputs.map(_.collect {
      case (id, m, fp @ EndpointInput.FixedPath(_, _, _))   => PathComponent(id, m, Left(fp))
      case (id, m, pc @ EndpointInput.PathCapture(_, _, _)) => PathComponent(id, m, Right(pc))
    })

    def collectChildren(level: Int, path: PathComponent): ResourceTree = {
      val children: Seq[PathComponent] = distinctBy(_.name) {
        endpointPathComponents
          .filter(pcs => pcs.lift(level).isDefined && pathMatches(pcs(level - 1), path))
          .map(_(level))
      }
      if (children.isEmpty) ResourceTree(path, Seq.empty)
      else ResourceTree(path, children.map(collectChildren(level + 1, _)))
    }

    val paths0: Seq[PathComponent] = distinctBy(pc => (pc.method, pc.name))(endpointPathComponents.flatMap(_.headOption))

    ResourceTree(RootPathComponent, paths0.map(collectChildren(1, _)))
  }

  private def distinctBy[D](value: PathComponent => D)(pcs: Seq[PathComponent]): Seq[PathComponent] =
    pcs.groupBy(value).flatMap { case (_, pcs) => pcs.headOption }.toSeq
}

case class ResourceTree(path: PathComponent, children: Seq[ResourceTree])

object Dupa extends App {
  def show(r: ResourceTree): Unit = {
    def showResource(nest: Int = 1, r: ResourceTree): Unit = {
      if (r.children.nonEmpty) {
        r.children.foreach { c =>
          println("  " * nest + c.path.name + " " + c.path.method.method)
          showResource(nest + 1, c)
        }
      }
    }
    println(r.path.name + " " + r.path.method.method)
    showResource(1, r)
  }

  import sttp.tapir._
  import sttp.tapir.internal._

  val eps = List(
    endpoint.get.in("accounts" / path[String]("id")),
    endpoint.post.in("accounts"),
    endpoint.get.in("accounts" / path[String]("id") / "transactions"),
    endpoint.post.in("accounts" / path[String]("id") / "transactions")
  )

  val epsBasicInputs: Seq[(Endpoint[_, _, _, _], Vector[IdMethodEndpointInput])] = eps.map { ep =>
    ep -> ep.input.asVectorOfBasicInputs().map(input => (UUID.randomUUID().toString, ep.httpMethod.getOrElse(Method("ANY")), input))
  }

  val tree = ApiResourceTree(epsBasicInputs.map(_._2))

  show(tree)
}
