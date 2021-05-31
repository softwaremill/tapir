package sttp.tapir.serverless.aws.sam

import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint

trait AwsSamInterpreter {
  def toSamTemplate[I, E, O, S](e: Endpoint[I, E, O, S])(implicit options: AwsSamOptions): SamTemplate = EndpointsToSamTemplate(List(e))

  def toSamTemplate(es: Iterable[Endpoint[_, _, _, _]])(implicit options: AwsSamOptions): SamTemplate = EndpointsToSamTemplate(es.toList)

  def toSamTemplate[I, E, O, S, F[_]](se: ServerEndpoint[I, E, O, S, F])(implicit options: AwsSamOptions): SamTemplate =
    EndpointsToSamTemplate(
      List(se.endpoint)
    )

  def serverEndpointsToSamTemplate[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]])(implicit options: AwsSamOptions): SamTemplate =
    EndpointsToSamTemplate(ses.map(_.endpoint).toList)
}

object AwsSamInterpreter extends AwsSamInterpreter