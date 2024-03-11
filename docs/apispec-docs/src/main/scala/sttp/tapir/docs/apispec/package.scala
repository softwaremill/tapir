package sttp.tapir.docs

import sttp.apispec.{ExampleMultipleValue, ExampleSingleValue, ExampleValue, SecurityScheme}
import sttp.tapir.{AnyEndpoint, Codec, EndpointInput, Schema, SchemaType}

import java.nio.ByteBuffer
import java.nio.charset.Charset

package object apispec {
  private[docs] type SchemeName = String
  private[docs] type SecuritySchemes = Map[EndpointInput.Auth[_, _], (SchemeName, SecurityScheme)]

  private[docs] def uniqueString(base: String, isUnique: String => Boolean): String = {
    var i = 0
    var result = base
    while (!isUnique(result)) {
      i += 1
      result = base + i
    }
    result
  }

  private def rawToString(v: Any): String = v match {
    case a: Array[Byte] => new String(a, "UTF-8")
    case b: ByteBuffer  => Charset.forName("UTF-8").decode(b).toString
    case _              => v.toString
  }

  private[docs] def exampleValue(v: String): ExampleValue = ExampleSingleValue(v)
  private[docs] def exampleValue[T](codec: Codec[_, T, _], e: T): Option[ExampleValue] = exampleValue(codec.schema, codec.encode(e))
  private[docs] def exampleValue(schema: Schema[_], raw: Any): Option[ExampleValue] = {
    // #3581: if there's a delimiter and the encoded value is a string, the codec will have produced a final
    // representation (with the delimiter applied), but in the docs we want to show the split values
    val rawDelimited = schema.attribute(Schema.Delimiter.Attribute) match {
      case None => raw
      case Some(Schema.Delimiter(d)) =>
        raw match {
          case s: String       => s.split(d).toSeq
          case List(s: String) => s.split(d).toSeq
          case _               => raw
        }
    }

    (rawDelimited, schema.schemaType) match {
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
        case p: EndpointInput.PathCapture[_] if !p.codec.schema.hidden => Left(p.name)
        case p: EndpointInput.FixedPath[_] if !p.codec.schema.hidden   => Right(p.s)
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
