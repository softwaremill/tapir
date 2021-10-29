package sttp.tapir.serverless.aws.sam

import sttp.tapir.{AnyEndpoint, Endpoint}
import sttp.tapir.server.ServerEndpoint

trait AwsSamInterpreter {

  def awsSamOptions: AwsSamOptions

  def toSamTemplate[I, E, O, S](e: Endpoint[I, E, O, S]): SamTemplate = EndpointsToSamTemplate(List(e), awsSamOptions)

  def toSamTemplate(es: Iterable[AnyEndpoint]): SamTemplate = EndpointsToSamTemplate(es.toList, awsSamOptions)

  def toSamTemplate[I, E, O, S, F[_]](se: ServerEndpoint[I, E, O, S, F]): SamTemplate =
    EndpointsToSamTemplate(
      List(se.endpoint),
      awsSamOptions
    )

  def serverEndpointsToSamTemplate[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]]): SamTemplate =
    EndpointsToSamTemplate(ses.map(_.endpoint).toList, awsSamOptions)
}

object AwsSamInterpreter {
  def apply(samOptions: AwsSamOptions): AwsSamInterpreter = {
    new AwsSamInterpreter {
      override def awsSamOptions: AwsSamOptions = samOptions
    }
  }
}
