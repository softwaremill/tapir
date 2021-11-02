package sttp.tapir.serverless.aws.sam

import sttp.tapir.{AnyEndpoint, Endpoint}
import sttp.tapir.server.ServerEndpoint

trait AwsSamInterpreter {

  def awsSamOptions: AwsSamOptions

  def toSamTemplate[A, I, E, O, R](e: Endpoint[A, I, E, O, R]): SamTemplate = EndpointsToSamTemplate(List(e), awsSamOptions)

  def toSamTemplate(es: Iterable[AnyEndpoint]): SamTemplate = EndpointsToSamTemplate(es.toList, awsSamOptions)

  def toSamTemplate[R, F[_]](se: ServerEndpoint[R, F]): SamTemplate =
    EndpointsToSamTemplate(
      List(se.endpoint),
      awsSamOptions
    )

  def serverEndpointsToSamTemplate[F[_]](ses: Iterable[ServerEndpoint[_, F]]): SamTemplate =
    EndpointsToSamTemplate(ses.map(_.endpoint).toList, awsSamOptions)
}

object AwsSamInterpreter {
  def apply(samOptions: AwsSamOptions): AwsSamInterpreter = {
    new AwsSamInterpreter {
      override def awsSamOptions: AwsSamOptions = samOptions
    }
  }
}
