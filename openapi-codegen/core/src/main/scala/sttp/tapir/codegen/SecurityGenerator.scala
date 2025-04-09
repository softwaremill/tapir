package sttp.tapir.codegen

import sttp.tapir.codegen.BasicGenerator.indent
import sttp.tapir.codegen.openapi.models.OpenapiSecuritySchemeType
import sttp.tapir.codegen.openapi.models.OpenapiSecuritySchemeType.OAuth2FlowType
import sttp.tapir.codegen.util.ErrUtils.bail
import sttp.tapir.codegen.util.Location

import scala.collection.immutable

case class SecurityInnerDefn(schemas: Seq[String]) {
  lazy val isEmpty: Boolean = schemas.isEmpty
  lazy val isSingleton: Boolean = schemas.size == 1
  lazy val partialTypeName: String = if (isEmpty) "Empty" else if (isSingleton) schemas.head else schemas.mkString("_and_")
  lazy val typeName: String = s"${partialTypeName.capitalize}SecurityIn"
  lazy val argType: String =
    if (isEmpty) "Unit"
    else if (isSingleton) SecurityGenerator.typeFromAuthType(schemas.head)
    else schemas.map(SecurityGenerator.typeFromAuthType).mkString("(", ", ", ")")
  lazy val unzippedArgTypes: String =
    schemas.map { s => s"Option[${SecurityGenerator.typeFromAuthType(s)}]" }.mkString("(", ", ", ")")
}
case class SecurityWrapperDefn(schemas: Set[SecurityInnerDefn]) {
  lazy val traitName: String = schemas.map(_.partialTypeName).toSeq.sorted.mkString("_or_") + "_SecurityIn"
}
case class SecurityDefn(
    inDecl: Option[String],
    tpe: Option[String],
    wrapperDefinitions: Option[SecurityWrapperDefn]
)

object SecurityGenerator {
  def typeFromAuthType(s: String) = if (s.equalsIgnoreCase("Basic")) "UsernamePassword" else "String"
  def mkMapImpl(as: Seq[String]) = {
    val innerDefn = SecurityInnerDefn(as)
    val unzippedSomes = innerDefn.schemas.zipWithIndex.map { case (x, i) => s"Some(v$i: ${typeFromAuthType(x)})" }.mkString(", ")
    val unzippedNones = innerDefn.schemas.map(_ => "None").mkString(", ")
    val zippedArgs = innerDefn.schemas.zipWithIndex.map { case (x, i) => s"v$i: ${typeFromAuthType(x)}" }.mkString("(", ", ", ")")
    s"""sttp.tapir.Mapping.from[${innerDefn.unzippedArgTypes}, Option[${innerDefn.argType}]] {
       |    case ($unzippedSomes) => Some($zippedArgs)
       |    case _ => None
       |  } {
       |    case Some($zippedArgs) => ($unzippedSomes)
       |    case None => ($unzippedNones)
       |  }""".stripMargin
  }

  def genSecurityTypes(securityWrappers: Set[SecurityWrapperDefn]): String = {
    val allSchemes = securityWrappers.flatMap(_.schemas)
    val allTpes = allSchemes.toSeq.sortBy(_.typeName)
    val tpesWithParents = allTpes.map { t =>
      t -> securityWrappers.filter(_.schemas.contains(t)).map(_.traitName).toSeq.sorted.mkString(" with ")
    }
    val traits = securityWrappers.map(_.traitName).toSeq.sorted.map(tn => s"sealed trait $tn").mkString("\n")
    val classes = tpesWithParents
      .map {
        case (d, ps) if d.argType == "Unit" => s"case object EmptySecurityIn extends $ps"
        case (d, ps)                        => s"case class ${d.typeName}(value: ${d.argType}) extends $ps"
      }
      .mkString("\n")

    val mappings = allSchemes
      .filterNot(_.isSingleton)
      .map { t =>
        val mapImpl = mkMapImpl(t.schemas)
        s"""val ${t.typeName}Mapping = $mapImpl"""
      }
      .mkString("\n")
    s"$traits\n$classes\n$mappings"
  }

  def security(securitySchemes: Map[String, OpenapiSecuritySchemeType], security: Seq[Map[String, Seq[String]]])(implicit
      location: Location
  ): SecurityDefn = {
    if (security.forall(_.isEmpty)) return SecurityDefn(None, None, None)
    val securityIsOptional = security.exists(_.nonEmpty) && security.exists(_.isEmpty)

    // Would be nice to do something to respect scopes here
    def inner(multi: Boolean = false): immutable.Seq[Seq[(String, String, String)]] = security
      .map(_.flatMap { case (schemeName, _ /*scopes*/ ) =>
        def wrap(s: String): String = if (multi || securityIsOptional) s"Option[$s]" else s
        securitySchemes.get(schemeName) match {
          case Some(OpenapiSecuritySchemeType.OpenapiSecuritySchemeBearerType) =>
            Seq((s"auth.bearer[${wrap("String")}]()", "Bearer", schemeName))

          case Some(OpenapiSecuritySchemeType.OpenapiSecuritySchemeBasicType) =>
            Seq(("auth.basic[UsernamePassword]()", "Basic", schemeName))

          case Some(OpenapiSecuritySchemeType.OpenapiSecuritySchemeApiKeyType(in, name)) =>
            Seq((s"""auth.apiKey($in[${wrap("String")}]("$name"))""", schemeName, schemeName))

          case Some(OpenapiSecuritySchemeType.OpenapiSecuritySchemeOAuth2Type(flows)) if flows.isEmpty => Nil
          case Some(OpenapiSecuritySchemeType.OpenapiSecuritySchemeOAuth2Type(flows)) =>
            flows.map {
              case (_, _) if multi              => (s"auth.bearer[${wrap("String")}]()", "Bearer", schemeName)
              case (OAuth2FlowType.password, _) => (s"auth.bearer[${wrap("String")}]()", "Bearer", schemeName)
              case (OAuth2FlowType.`implicit`, f) =>
                val authUrl = f.authorizationUrl.getOrElse(bail("authorizationUrl required for implicit flow"))
                val refreshUrl = f.refreshUrl.map(u => s"""Some("$u")""").getOrElse("None")
                (s"""auth.oauth2.implicitFlow("$authUrl", $refreshUrl)""", "Bearer", schemeName)
              case (OAuth2FlowType.clientCredentials, f) =>
                val tokenUrl = f.tokenUrl.getOrElse(bail("tokenUrl required for clientCredentials flow"))
                val refreshUrl = f.refreshUrl.map(u => s"""Some("$u")""").getOrElse("None")
                (s"""auth.oauth2.clientCredentialsFlow("$tokenUrl", $refreshUrl)""", "Bearer", schemeName)
              case (OAuth2FlowType.authorizationCode, f) =>
                val authUrl = f.authorizationUrl.getOrElse(bail("authorizationUrl required for authorizationCode flow"))
                val tokenUrl = f.tokenUrl.getOrElse(bail("tokenUrl required for authorizationCode flow"))
                val refreshUrl = f.refreshUrl.map(u => s"""Some("$u")""").getOrElse("None")
                (s"""auth.oauth2.authorizationCodeFlow("$authUrl", "$tokenUrl", $refreshUrl)""", "Bearer", schemeName)
            }

          case None =>
            bail(s"Unknown security scheme $schemeName!")
        }
      }.toList)
      .toList

    inner().distinct match {
      case Nil                          => SecurityDefn(None, None, None)
      case Seq((h, authType, _)) +: Nil => SecurityDefn(Some(s".securityIn($h)"), Some(typeFromAuthType(authType)), None)
      case s =>
        def handleMultiple = {
          val isMultiOr: Boolean = s.count(_.nonEmpty) > 1
          def wrapT(s: String): String = if (isMultiOr || securityIsOptional) s"Option[$s]" else s
          val optionally = inner(isMultiOr).groupBy(_.map(_._2).sorted.distinct).values.map(_.head).toSeq
          val namesImplsAndTypes = optionally.map(_.sortBy(_._1)).toList.sortBy(_.map(_._1).mkString(",")).map { x =>
            val y = x.map {
              case (_, "Basic", _) =>
                ("Basic", s"auth.basic[${wrapT("UsernamePassword")}]()", "UsernamePassword")
              case (_, "Bearer", _) =>
                ("Bearer", s"auth.bearer[${wrapT("String")}]()", "String")
              case (impl, _, name) => (name, impl, "String")
            }
            if (y.isEmpty) {
              (SecurityInnerDefn(Nil), "", "None")
            } else if (y.size == 1) {
              val (a, b, c) = y.head
              (SecurityInnerDefn(Seq(a)), s".securityIn($b)", wrapT(c))
            } else {
              val (as, bs, _) = y.unzip3
              val innerDefn = SecurityInnerDefn(as)
              val unmappedImpl = bs.reduceLeft((a, n) => s"$a\n  .and($n)")
              val mapImpl = if (isMultiOr || securityIsOptional) s".map(${innerDefn.typeName}Mapping)" else ""
              val impl = s"$unmappedImpl$mapImpl"
              (innerDefn, s".securityIn($impl)", wrapT(innerDefn.argType))
            }
          }
          val nonEmptyDeclarations = namesImplsAndTypes.filterNot(_._3 == "None")
          val impls = nonEmptyDeclarations.map(_._2).mkString("\n")
          val traitName = SecurityWrapperDefn(namesImplsAndTypes.map(_._1).toSet).traitName
          val mapImpl =
            if (isMultiOr) {
              val count = nonEmptyDeclarations.size
              def someAt(idx: Int, othersAreNone: Boolean) = (0 until count)
                .map { case `idx` => "Some(x)"; case _ => if (othersAreNone) "None" else "_" }
                .mkString("(", ", ", ")")
              def allNones = (0 until count).map(_ => "None").mkString("(", ", ", ")")
              val mapDecodes = nonEmptyDeclarations.zipWithIndex.map { case ((name, _, _), idx) =>
                s"case ${someAt(idx, name.isSingleton)} => DecodeResult.Value(${name.typeName.capitalize}(x))"
              }
              val decodeNone = if (securityIsOptional) s"\ncase $allNones => DecodeResult.Value(EmptySecurityIn)" else ""
              val mapEncodes = nonEmptyDeclarations.zipWithIndex.map { case ((name, _, _), idx) =>
                s"case ${name.typeName}(x) => ${someAt(idx, true)}"
              }
              val encodeNone = if (securityIsOptional) s"\ncase EmptySecurityIn => DecodeResult.Value($allNones)" else ""
              s"""
                 |.mapSecurityInDecode[$traitName]{
                 |${indent(2)(mapDecodes.mkString("\n") + decodeNone)}
                 |  case other =>
                 |    val count = other.productIterator.count(_.isInstanceOf[Some[?]])
                 |    DecodeResult.Error(s"$$count security inputs", new RuntimeException(s"Expected a single security input, found $$count"))
                 |}{
                 |${indent(2)(mapEncodes.mkString("\n") + encodeNone)}
                 |}""".stripMargin
            } else ""
          val securityWrappers = if (isMultiOr) Some(SecurityWrapperDefn(namesImplsAndTypes.map(_._1).toSet)) else None
          val tpe = if (isMultiOr) Some(traitName) else nonEmptyDeclarations.map(_._1.argType).headOption.map(wrapT)
          SecurityDefn(Some(s"$impls$mapImpl"), tpe, securityWrappers)
        }
        s.map(_.map(_._2).distinct).distinct match {
          case h +: Nil =>
            h match {
              case Seq("Bearer") => SecurityDefn(Some(".securityIn(auth.bearer[String]())"), Some("String"), None)
              case Seq("Basic")  => SecurityDefn(Some(".securityIn(auth.basic[UsernamePassword]())"), Some("UsernamePassword"), None)
              case _             => handleMultiple
            }
          case _ => handleMultiple
        }
    }
  }
}
