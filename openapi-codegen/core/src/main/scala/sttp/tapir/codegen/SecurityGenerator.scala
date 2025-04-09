package sttp.tapir.codegen

import sttp.tapir.codegen.BasicGenerator.indent
import sttp.tapir.codegen.openapi.models.OpenapiSecuritySchemeType
import sttp.tapir.codegen.openapi.models.OpenapiSecuritySchemeType.OAuth2FlowType
import sttp.tapir.codegen.util.ErrUtils.bail
import sttp.tapir.codegen.util.Location

import scala.collection.immutable

object SecurityGenerator {

  def security(securitySchemes: Map[String, OpenapiSecuritySchemeType], security: Seq[Map[String, Seq[String]]])(implicit
      location: Location
  ): SecurityDefn = {
    if (security.forall(_.isEmpty)) return SecurityDefn(None, None, None)
    if (security.exists(_.size > 1)) bail("Multiple schemes on same security requirement not yet supported")
    if (security.exists(_.size == 1) && security.exists(_.isEmpty))
      bail("Anonymous access not supported on endpoints with other security requirement declarations")
    if (security.map(_.head._1).distinct.size != security.size)
      bail("Multiple security requirements are only supported if schemes differ between requirements")

    // Would be nice to do something to respect scopes here
    def inner(multi: Boolean = false): immutable.Seq[(String, String, String)] = security
      .map(_.head) // we know, at this point, that all security decls have a since scheme
      .flatMap { case (schemeName, _ /*scopes*/ ) =>
        def wrap(s: String): String = if (multi) s"Option[$s]" else s
        securitySchemes.get(schemeName) match {
          case Some(OpenapiSecuritySchemeType.OpenapiSecuritySchemeBearerType) =>
            Seq((s"auth.bearer[${wrap("String")}]()", "Bearer", schemeName))

          case Some(OpenapiSecuritySchemeType.OpenapiSecuritySchemeBasicType) =>
            Seq(("auth.basic[UsernamePassword]()", "Basic", schemeName))

          case Some(OpenapiSecuritySchemeType.OpenapiSecuritySchemeApiKeyType(in, name)) =>
            Seq((s"""auth.apiKey($in[${wrap("String")}]("$name"))""", "ApiKey", schemeName))

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
      }
      .toList

    inner().distinct match {
      case Nil                    => SecurityDefn(None, None, None)
      case (h, "Basic", _) +: Nil => SecurityDefn(Some(s".securityIn($h)"), Some("UsernamePassword"), None)
      case (h, _, _) +: Nil       => SecurityDefn(Some(s".securityIn($h)"), Some("String"), None)
      case s =>
        def handleMultiple = {
          val optionally = inner(true).distinct.groupBy(_._2)
          val namesAndImpls = optionally.toList.sortBy(_._1).flatMap {
            case ("Bearer", _)  => Seq("Bearer" -> s".securityIn(auth.bearer[Option[String]]())")
            case ("Basic", _)   => Seq("Basic" -> s".securityIn(auth.basic[Option[UsernamePassword]]())")
            case ("ApiKey", vs) => vs.map { case (impl, _, name) => name -> s".securityIn($impl)" }.sortBy(_._1)
          }
          val impls = namesAndImpls.map(_._2).mkString("\n")
          val traitName = SecurityWrapperDefn(namesAndImpls.map(_._1).toSet).traitName
          val count = namesAndImpls.size
          def someAt(idx: Int) = (0 to count - 1)
            .map { case `idx` => "Some(x)"; case _ => "None" }
            .mkString("(", ", ", ")")
          val mapDecodes = namesAndImpls.zipWithIndex.map { case ((name, _), idx) =>
            s"case ${someAt(idx)} => DecodeResult.Value(${name.capitalize}SecurityIn(x))"
          }
          val mapEncodes = namesAndImpls.zipWithIndex.map { case ((name, _), idx) =>
            s"case ${name.capitalize}SecurityIn(x) => ${someAt(idx)}"
          }
          val mapImpl =
            s""".mapSecurityInDecode[$traitName]{
               |${indent(2)(mapDecodes.mkString("\n"))}
               |  case other =>
               |    val count = other.productIterator.count(_.isInstanceOf[Some[?]])
               |    DecodeResult.Error(s"$$count security inputs", new RuntimeException(s"Expected a single security input, found $$count"))
               |}{
               |${indent(2)(mapEncodes.mkString("\n"))}
               |}""".stripMargin
          SecurityDefn(Some(s"$impls\n$mapImpl"), Some(traitName), Some(SecurityWrapperDefn(namesAndImpls.map(_._1).toSet)))
        }
        s.map(_._2).distinct match {
          case h +: Nil =>
            h match {
              case "Bearer" => SecurityDefn(Some(".securityIn(auth.bearer[String]())"), Some("String"), None)
              case "Basic"  => SecurityDefn(Some(".securityIn(auth.basic[UsernamePassword]())"), Some("UsernamePassword"), None)
              case _        => handleMultiple
            }
          case _ => handleMultiple
        }
    }
  }
}
