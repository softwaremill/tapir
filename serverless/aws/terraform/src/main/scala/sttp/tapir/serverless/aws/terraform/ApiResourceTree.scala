package sttp.tapir.serverless.aws.terraform

import sttp.model.Method
import sttp.tapir.serverless.aws.terraform.EndpointsToTerraformConfig.IdMethodEndpointInput
import sttp.tapir.{Codec, EndpointIO, EndpointInput}

private[terraform] object ApiResourceTree {
  val RootPathComponent: PathComponent =
    PathComponent("root", Method("ANY"), Left(EndpointInput.FixedPath("/", Codec.idPlain(), EndpointIO.Info.empty)))

  private val pathMatches: (PathComponent, PathComponent) => Boolean = (a, b) => {
    (a, b) match {
      case (PathComponent(_, m1, Left(fp1)), PathComponent(_, m2, Left(fp2)))   => fp1.s == fp2.s && m1 == m2
      case (PathComponent(_, m1, Right(pc1)), PathComponent(_, m2, Right(pc2))) => pc1.name == pc2.name && m1 == m2
      case _                                                                    => false
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

  private def distinctBy[V](value: PathComponent => V)(pcs: Seq[PathComponent]): Seq[PathComponent] =
    pcs.groupBy(value).flatMap { case (_, pcs) => pcs.headOption }.toSeq
}

case class ResourceTree(path: PathComponent, children: Seq[ResourceTree])
