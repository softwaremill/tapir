package sttp.tapir.codegen.endpoints

import sttp.tapir.codegen.dedup.PackageReuseContext
import sttp.tapir.codegen.endpoints.SimpleTypes.mapSchemaSimpleTypeToType
import sttp.tapir.codegen.json.JsonSerdeLib.JsonSerdeLib
import sttp.tapir.codegen.openapi.models.OpenapiModels.{OpenapiDocument, OpenapiParameter}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{OpenapiSchemaEnum, OpenapiSchemaSimpleType}
import sttp.tapir.codegen.util.ErrUtils.bail
import sttp.tapir.codegen.util.{JavaEscape, Location}
import sttp.tapir.codegen.validation.ValidationGenerator

case class UrlMapResponse(
    pathDecl: String,
    pathTypes: Seq[String],
    maybeSecurityPath: Option[(String, Seq[String])],
    inlineUrlDefs: Seq[String]
)

object UrlEndpointComponent {

  private[endpoints] def urlMapper(
      url: String,
      endpointName: String,
      targetScala3: Boolean,
      jsonSerdeLib: JsonSerdeLib,
      parameters: Seq[OpenapiParameter],
      securityPathPrefixes: Seq[String],
      doc: OpenapiDocument,
      generateValidators: Boolean,
      isReused: Boolean,
      packageReuse: PackageReuseContext
  )(implicit
      location: Location
  ): UrlMapResponse = {
    val securityPrefixes = securityPathPrefixes.filter(url.startsWith)
    // .in(("books" / path[String]("genre") / path[Int]("year")).mapTo[BooksFromYear])
    val maxSecurityPrefix = if (securityPrefixes.nonEmpty) Some(securityPrefixes.maxBy(_.length)) else None
    val inUrl = maxSecurityPrefix.fold(url)(url.stripPrefix)
    def toPathDecl(url: String): (Array[String], Array[Option[String]], Array[Option[String]]) = url
      .split('/')
      .filter(_.nonEmpty)
      .map { segment =>
        if (segment.startsWith("{")) {
          val name = segment.drop(1).dropRight(1)
          val param = parameters.find(_.name == name)
          param.fold(bail(s"URLParam $name not found!")) { p =>
            p.schema match {
              case st: OpenapiSchemaSimpleType =>
                val (t, _) = mapSchemaSimpleTypeToType(st)
                val desc = p.description.fold("")(d => s""".description("${JavaEscape.escapeString(d)}")""")
                val validations = if (generateValidators) ValidationGenerator.mkValidations(doc, st, true) else ""
                (s"""path[$t]("${JavaEscape.escapeString(name)}")$validations$desc""", Some(t), None)
              case e: OpenapiSchemaEnum =>
                val (param, inlineDefn, tpe, enumName) =
                  ParamComponent.getEnumParamDefn(endpointName, targetScala3, jsonSerdeLib, p, e, false)
                val defns =
                  if (isReused)
                    Some(
                      s"type $enumName = ${packageReuse.dependencyModelPath}.$enumName\nval $enumName = ${packageReuse.dependencyModelPath}.$enumName"
                    )
                  else inlineDefn.map(_.mkString("\n"))
                (param, Some(tpe), defns)
              case _ => bail("Can't create non-simple params to url yet")
            }
          }
        } else {
          // Literal path segments come from the (untrusted) `paths` keys and are emitted into a string literal, so
          // escape them like the adjacent path-parameter and description sites. See GHSA-gpcc-36pq-8qxr.
          (JavaEscape.quote(segment), None, None)
        }
      }
      .unzip3
    val (inPath, tpes, inlineDefns) = toPathDecl(inUrl)
    val inPathDecl = if (inPath.nonEmpty) ".in((" + inPath.mkString(" / ") + "))" else ""
    val (secPathDecl, secInlineDefs) =
      maxSecurityPrefix
        .map(toPathDecl)
        .map { case (ds, ts, inline) => (".prependSecurityIn(" + ds.mkString(" / ") + ")" -> ts.toSeq.flatten) -> inline }
        .unzip
    val flatInlineSecDefs: Seq[String] = secInlineDefs.toSeq.flatten.flatten
    val flatInlineDefs: Seq[String] = inlineDefns.toSeq.flatten
    UrlMapResponse(inPathDecl, tpes.toSeq.flatten, secPathDecl.headOption, flatInlineSecDefs ++ flatInlineDefs)
  }
}
