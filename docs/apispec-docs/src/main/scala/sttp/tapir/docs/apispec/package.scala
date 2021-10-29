package sttp.tapir.docs

import sttp.tapir.Schema.SName
import sttp.tapir.apispec.{ExampleMultipleValue, ExampleSingleValue, ExampleValue, SecurityScheme}
import sttp.tapir.{AnyEndpoint, Codec, EndpointInput, Schema, SchemaType}

package object apispec {
  private[docs] type SchemeName = String
  private[docs] type SecuritySchemes = Map[EndpointInput.Auth[_], (SchemeName, SecurityScheme)]

  private[docs] val defaultSchemaName: SName => String = info => {
    val shortName = info.fullName.split('.').last
    (shortName +: info.typeParameterShortNames).mkString("_")
  }

  private[docs] def uniqueName(base: String, isUnique: String => Boolean): String = {
    var i = 0
    var result = base
    while (!isUnique(result)) {
      i += 1
      result = base + i
    }
    result
  }

  private def rawToString[T](v: Any): String = v.toString

  private[docs] def exampleValue[T](v: String): ExampleValue = ExampleSingleValue(v)
  private[docs] def exampleValue[T](codec: Codec[_, T, _], e: T): Option[ExampleValue] = exampleValue(codec.schema, codec.encode(e))
  private[docs] def exampleValue[T](schema: Schema[_], raw: Any): Option[ExampleValue] = {
    (raw, schema.schemaType) match {
      case (it: Iterable[_], SchemaType.SArray(_)) => Some(ExampleMultipleValue(it.map(rawToString).toList))
      case (it: Iterable[_], _)                    => it.headOption.map(v => ExampleSingleValue(rawToString(v)))
      case (it: Option[_], _)                      => it.map(v => ExampleSingleValue(rawToString(v)))
      case (v, _)                                  => Some(ExampleSingleValue(rawToString(v)))
    }
  }

  private[docs] def nameAllPathCapturesInEndpoint(e: AnyEndpoint): AnyEndpoint =
    e.copy(securityInput = namePathCapturesInInput(e.securityInput), input = namePathCapturesInInput(e.input))

  private def namePathCapturesInInput(i: EndpointInput[_]): EndpointInput[_] =
    new EndpointInputMapper[Int](
      { case (EndpointInput.PathCapture(None, codec, info), i) =>
        (EndpointInput.PathCapture(Some(s"p$i"), codec, info), i + 1)
      },
      PartialFunction.empty
    ).mapInput(i, 1)._1

  private[docs] def namedPathComponents(inputs: Vector[EndpointInput.Basic[_]]): Vector[String] = {
    inputs
      .collect {
        case EndpointInput.PathCapture(name, _, _) => Left(name)
        case EndpointInput.FixedPath(s, _, _)      => Right(s)
      }
      .foldLeft(Vector.empty[String]) { case (acc, component) =>
        component match {
          case Left(None)    => throw new IllegalStateException("All path captures should be named")
          case Left(Some(p)) => acc :+ p
          case Right(p)      => acc :+ p
        }
      }
  }
}
