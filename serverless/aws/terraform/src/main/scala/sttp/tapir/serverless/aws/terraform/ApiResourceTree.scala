package sttp.tapir.serverless.aws.terraform

import sttp.tapir.serverless.aws.terraform.EndpointsToTerraformConfig.IdEndpointInput
import sttp.tapir.{Codec, EndpointIO, EndpointInput}

/** Creates the tree representation of routes for given endpoints represented as basic inputs */
private[terraform] object ApiResourceTree {
  val RootPath = "/"
  val RootPathComponent: PathComponent =
    PathComponent("root", Left(EndpointInput.FixedPath(RootPath, Codec.idPlain(), EndpointIO.Info.empty)))

  private val pathMatches: (PathComponent, PathComponent) => Boolean = (a, b) => {
    (a.component, b.component) match {
      case (Left(fp1), Left(fp2))   => fp1.s == fp2.s
      case (Right(pc1), Right(pc2)) => pc1.name == pc2.name
      case _                        => false
    }
  }

  def apply(basicInputs: Seq[Vector[IdEndpointInput]]): ResourceTree = {

    val endpointPathComponents: Seq[Seq[PathComponent]] = basicInputs.map(_.collect {
      case (id, fp @ EndpointInput.FixedPath(_, _, _))   => PathComponent(id, Left(fp))
      case (id, pc @ EndpointInput.PathCapture(_, _, _)) => PathComponent(id, Right(pc))
    })

    def getChildren(level: Int, path: PathComponent): ResourceTree = {
      val children: Seq[PathComponent] = endpointPathComponents
        .filter(pcs => pcs.lift(level).isDefined && pathMatches(pcs(level - 1), path))
        .map(_(level))
        .groupBy(_.name)
        .flatMap { case (_, pcs) => pcs.headOption }
        .toSeq
      if (children.isEmpty) ResourceTree(path, Seq.empty)
      else ResourceTree(path, children.map(getChildren(level + 1, _)))
    }

    val paths0: Seq[PathComponent] =
      endpointPathComponents.flatMap(_.headOption).groupBy(_.name).flatMap { case (_, pcs) => pcs.headOption }.toSeq

    ResourceTree(RootPathComponent, paths0.map(getChildren(1, _)))
  }
}

case class ResourceTree(pathComponent: PathComponent, children: Seq[ResourceTree])
