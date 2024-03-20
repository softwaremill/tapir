package sttp.tapir.internal

import sttp.tapir._
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.typelevel.ParamConcat
import sttp.model.{QueryParams, Header, StatusCode}
import sttp.model.headers.{Cookie, CookieWithMeta, CookieValueWithMeta}

import scala.collection.mutable
import scala.quoted.*

private[tapir] class AnnotationsMacros[T <: Product: Type](using q: Quotes) {
  import quotes.reflect.*

  private val caseClass = new CaseClass[q.type, T](using summon[Type[T]], q)

  def deriveEndpointInputImpl: Expr[EndpointInput[T]] = {
    // the path inputs must be defined in the order as they appear in the argument to @endpointInput
    val pathSegments = caseClass
      .extractOptStringArgFromAnnotation(endpointInputAnnotationSymbol)
      .flatten
      .map { case path =>
        val pathWithoutLeadingSlash = if (path.startsWith("/")) path.drop(1) else path
        val result = if (pathWithoutLeadingSlash.endsWith("/")) pathWithoutLeadingSlash.dropRight(1) else pathWithoutLeadingSlash
        if (result.length == 0) Nil else result.split("/").toList
      }
      .getOrElse(Nil)

    val fieldsWithIndex = caseClass.fields.zipWithIndex
    val inputIdxToFieldIdx = mutable.Map.empty[Int, Int]

    val pathInputs = pathSegments.map { segment =>
      if (segment.startsWith("{") && segment.endsWith("}")) {
        val fieldName = segment.drop(1).dropRight(1)
        fieldsWithIndex.find { case (f, _) => f.name == fieldName && f.annotated(pathAnnotationSymbol) } match {
          case Some((field, idx)) =>
            field.tpe.asType match
              case '[f] =>
                inputIdxToFieldIdx += (inputIdxToFieldIdx.size -> idx)
                wrapInput[f](field, makePathInput[f](field))
          case None =>
            report.errorAndAbort(s"${caseClass.name}.${fieldName} must have the @path annotation, as it is referenced from @endpointInput.")
        }
      } else {
        makeFixedPath(segment)
      }
    }

    val (pathFields, nonPathFields) = fieldsWithIndex.partition((f, _) => f.annotated(pathAnnotationSymbol))

    if (inputIdxToFieldIdx.size != pathFields.size) {
      report.errorAndAbort(s"Not all fields of ${caseClass.name} annotated with @path are captured in the path in @endpointInput.")
    }

    val nonPathInputs = nonPathFields.map { case (field, fieldIdx) =>
      field.tpe.asType match
        case '[f] =>
          val input: Expr[EndpointInput.Single[f]] = field
            .extractOptStringArgFromAnnotation(queryAnnotationSymbol)
            .map(makeQueryInput[f](field))
            .orElse(field.extractOptStringArgFromAnnotation(headerAnnotationSymbol).map(makeHeaderIO[f](field)))
            .orElse(field.extractOptStringArgFromAnnotation(cookieAnnotationSymbol).map(makeCookieInput[f](field)))
            .orElse(field.annotation(bodyAnnotationSymbol).map(makeBodyIO[f](field)))
            .orElse(if (field.annotated(paramsAnnotationSymbol)) Some(makeQueryParamsInput[f](field)) else None)
            .orElse(if (field.annotated(headersAnnotationSymbol)) Some(makeHeadersIO[f](field)) else None)
            .orElse(if (field.annotated(cookiesAnnotationSymbol)) Some(makeCookiesIO[f](field)) else None)
            .orElse(
              field
                .annotation(bearerAnnotationSymbol)
                .map(makeBearerAuthInput[f](field, field.annotation(securitySchemeNameAnnotationSymbol), _))
            )
            .orElse(
              field
                .annotation(basicAnnotationSymbol)
                .map(makeBasicAuthInput[f](field, field.annotation(securitySchemeNameAnnotationSymbol), _))
            )
            .getOrElse {
              report.errorAndAbort(
                s"All fields of ${caseClass.name} must be annotated with one of the annotations from sttp.tapir.annotations. No annotations for field: ${field.name}."
              )
            }
          inputIdxToFieldIdx += (inputIdxToFieldIdx.size -> fieldIdx)
          wrapInput[f](field, input)
    }

    val result = (pathInputs ::: nonPathInputs).map(_.asTerm).reduceLeft { (left, right) =>
      (left.tpe.asType, right.tpe.asType) match
        case ('[EndpointInput[l]], '[EndpointInput[r]]) =>
          // we need to summon explicitly to get the instance for the "real" l, r types
          val concat = Expr.summon[ParamConcat[l, r]].get
          concat.asTerm.tpe.asType match {
            case '[ParamConcat.Aux[`l`, `r`, lr]] =>
              '{
                ${ left.asExprOf[EndpointInput[l]] }
                  .and(${ right.asExprOf[EndpointInput[r]] })(using $concat.asInstanceOf[ParamConcat.Aux[l, r, lr]])
              }.asTerm
          }
    }

    result.tpe.asType match {
      case '[EndpointInput[r]] =>
        '{
          ${ result.asExprOf[EndpointInput[r]] }.map[T](${ mapToTargetFunc[r](inputIdxToFieldIdx) })(${
            mapFromTargetFunc[r](inputIdxToFieldIdx)
          })
        }
    }
  }

  def deriveEndpointOutputImpl: Expr[EndpointOutput[T]] = {
    val fieldsWithIndex = caseClass.fields.zipWithIndex

    val outputs = fieldsWithIndex.map { case (field, fieldIdx) =>
      field.tpe.asType match
        case '[f] =>
          val output: Expr[EndpointOutput.Single[f]] = field
            .extractOptStringArgFromAnnotation(headerAnnotationSymbol)
            .map(makeHeaderIO[f](field))
            .orElse(field.extractOptStringArgFromAnnotation(setCookieAnnotationSymbol).map(makeSetCookieOutput[f](field)))
            .orElse(field.annotation(bodyAnnotationSymbol).map(makeBodyIO[f](field)))
            .orElse(if (field.annotated(statusCodeAnnotationSymbol)) Some(makeStatusCodeOutput[f](field)) else None)
            .orElse(if (field.annotated(headersAnnotationSymbol)) Some(makeHeadersIO[f](field)) else None)
            .orElse(if (field.annotated(cookiesAnnotationSymbol)) Some(makeCookiesIO[f](field)) else None)
            .orElse(if (field.annotated(setCookiesAnnotationSymbol)) Some(makeSetCookiesOutput[f](field)) else None)
            .getOrElse {
              report.errorAndAbort(
                s"All fields of ${caseClass.name} must be annotated with one of the annotations from sttp.tapir.annotations. No annotations for field: ${field.name}."
              )
            }
          '{ ${ addMetadataFromAnnotations[f](field, output) }.asInstanceOf[EndpointOutput.Single[f]] }
    }

    val result = outputs.map(_.asTerm).reduceLeft { (left, right) =>
      (left.tpe.asType, right.tpe.asType) match
        case ('[EndpointOutput[l]], '[EndpointOutput[r]]) =>
          // we need to summon explicitly to get the instance for the "real" l, r types
          val concat = Expr.summon[ParamConcat[l, r]].get
          concat.asTerm.tpe.asType match {
            case '[ParamConcat.Aux[`l`, `r`, lr]] =>
              '{
                ${ left.asExprOf[EndpointOutput[l]] }
                  .and(${ right.asExprOf[EndpointOutput[r]] })(using $concat.asInstanceOf[ParamConcat.Aux[l, r, lr]])
              }.asTerm
          }
    }

    result.tpe.asType match {
      case '[EndpointOutput[r]] =>
        val inputIdxToFieldIdx = mutable.Map((0 until outputs.size).map(i => (i, i)): _*)
        '{
          ${ result.asExprOf[EndpointOutput[r]] }.map[T](${ mapToTargetFunc[r](inputIdxToFieldIdx) })(${
            mapFromTargetFunc[r](inputIdxToFieldIdx)
          })
        }
    }
  }

  // annotation symbols
  private val endpointInputAnnotationSymbol = TypeTree.of[EndpointIO.annotations.endpointInput].tpe.typeSymbol
  private val pathAnnotationSymbol = TypeTree.of[EndpointIO.annotations.path].tpe.typeSymbol
  private val queryAnnotationSymbol = TypeTree.of[EndpointIO.annotations.query].tpe.typeSymbol
  private val headerAnnotationSymbol = TypeTree.of[EndpointIO.annotations.header].tpe.typeSymbol
  private val headersAnnotationSymbol = TypeTree.of[EndpointIO.annotations.headers].tpe.typeSymbol
  private val cookieAnnotationSymbol = TypeTree.of[EndpointIO.annotations.cookie].tpe.typeSymbol
  private val cookiesAnnotationSymbol = TypeTree.of[EndpointIO.annotations.cookies].tpe.typeSymbol
  private val setCookieAnnotationSymbol = TypeTree.of[EndpointIO.annotations.setCookie].tpe.typeSymbol
  private val setCookiesAnnotationSymbol = TypeTree.of[EndpointIO.annotations.setCookies].tpe.typeSymbol
  private val paramsAnnotationSymbol = TypeTree.of[EndpointIO.annotations.params].tpe.typeSymbol
  private val bearerAnnotationSymbol = TypeTree.of[EndpointIO.annotations.bearer].tpe.typeSymbol
  private val basicAnnotationSymbol = TypeTree.of[EndpointIO.annotations.basic].tpe.typeSymbol
  private val apikeyAnnotationSymbol = TypeTree.of[EndpointIO.annotations.apikey].tpe.typeSymbol
  private val securitySchemeNameAnnotationSymbol = TypeTree.of[EndpointIO.annotations.securitySchemeName].tpe.typeSymbol
  private val bodyAnnotationSymbol = TypeTree.of[EndpointIO.annotations.body].tpe.typeSymbol
  private val statusCodeAnnotationSymbol = TypeTree.of[EndpointIO.annotations.statusCode].tpe.typeSymbol

  private val descriptionAnnotationSymbol = TypeTree.of[EndpointIO.annotations.description].tpe.typeSymbol
  private val exampleAnnotationSymbol = TypeTree.of[EndpointIO.annotations.example].tpe.typeSymbol
  private val customiseAnnotationSymbol = TypeTree.of[EndpointIO.annotations.customise].tpe.typeSymbol

  // schema symbols
  private val schemaDescriptionAnnotationSymbol = TypeTree.of[Schema.annotations.description].tpe.typeSymbol
  private val schemaEncodedExampleAnnotationSymbol = TypeTree.of[Schema.annotations.encodedExample].tpe.typeSymbol
  private val schemaDefaultAnnotationSymbol = TypeTree.of[Schema.annotations.default].tpe.typeSymbol
  private val schemaFormatAnnotationSymbol = TypeTree.of[Schema.annotations.format].tpe.typeSymbol
  private val schemaDeprecatedAnnotationSymbol = TypeTree.of[Schema.annotations.deprecated].tpe.typeSymbol
  private val schemaValidateAnnotationSymbol = TypeTree.of[Schema.annotations.validate].tpe.typeSymbol

  // util
  private def summonCodec[L: Type, H: Type, CF <: CodecFormat: Type](field: CaseClassField[q.type, T]): Expr[Codec[L, H, CF]] =
    Expr.summon[Codec[L, H, CF]].getOrElse {
      report.errorAndAbort(
        s"Cannot summon codec from: ${Type.show[L]}, to: ${Type.show[H]}, formatted as: ${Type.show[CF]}, for field: ${field.name}."
      )
    }

  private def summonMultipartCodec[H: Type](field: CaseClassField[q.type, T]): Expr[MultipartCodec[H]] =
    Expr.summon[MultipartCodec[H]].getOrElse {
      report.errorAndAbort(
        s"Cannot summon multipart codec for type: ${Type.show[H]}, for field: ${field.name}."
      )
    }

  // inputs & outputs
  private def makeQueryInput[f: Type](field: CaseClassField[q.type, T])(altName: Option[String]): Expr[EndpointInput.Basic[f]] = {
    val name = Expr(altName.getOrElse(field.name))
    '{ query[f]($name)(using ${ summonCodec[List[String], f, TextPlain](field) }) }
  }

  private def makeHeaderIO[f: Type](field: CaseClassField[q.type, T])(altName: Option[String]): Expr[EndpointIO.Basic[f]] = {
    val name = Expr(altName.getOrElse(field.name))
    '{ header[f]($name)(using ${ summonCodec[List[String], f, TextPlain](field) }) }
  }

  private def makeCookieInput[f: Type](field: CaseClassField[q.type, T])(altName: Option[String]): Expr[EndpointInput.Basic[f]] = {
    val name = Expr(altName.getOrElse(field.name))
    '{ cookie[f]($name)(using ${ summonCodec[Option[String], f, TextPlain](field) }) }
  }

  private def makeBodyIO[f: Type](field: CaseClassField[q.type, T])(ann: Term): Expr[EndpointIO.Basic[f]] = {
    val annExp = ann.asExprOf[EndpointIO.annotations.body[_, _]]

    ann.tpe.asType match {
      case '[EndpointIO.annotations.multipartBody] =>
        val codec = summonMultipartCodec[f](field)
        '{
          EndpointIO.Body[Seq[RawPart], f](
            $codec.rawBodyType,
            $codec.codec,
            EndpointIO.Info.empty
          )
        }
      case '[EndpointIO.annotations.body[bt, cf]] =>
        '{
          EndpointIO.Body[bt, f](
            $annExp.bodyType.asInstanceOf[RawBodyType[bt]],
            ${ summonCodec[bt, f, cf](field) }.asInstanceOf[Codec[bt, f, CodecFormat]],
            EndpointIO.Info.empty
          )
        }
    }
  }

  private def makeQueryParamsInput[f: Type](field: CaseClassField[q.type, T]): Expr[EndpointInput.Basic[f]] = {
    summon[Type[f]] match {
      case '[QueryParams] => '{ queryParams.asInstanceOf[EndpointInput.Basic[f]] }
      case _              => report.errorAndAbort(annotationErrorMsg[f, QueryParams]("@params", field))
    }
  }

  private def makeHeadersIO[f: Type](field: CaseClassField[q.type, T]): Expr[EndpointIO.Basic[f]] =
    summon[Type[f]] match {
      case '[List[Header]] => '{ headers.asInstanceOf[EndpointIO.Basic[f]] }
      case _               => report.errorAndAbort(annotationErrorMsg[f, List[Header]]("@headers", field))
    }

  private def makeCookiesIO[f: Type](field: CaseClassField[q.type, T]): Expr[EndpointIO.Basic[f]] =
    summon[Type[f]] match {
      case '[List[Cookie]] => '{ cookies.asInstanceOf[EndpointIO.Basic[f]] }
      case _               => report.errorAndAbort(annotationErrorMsg[f, List[Cookie]]("@cookies", field))
    }

  private def makePathInput[f: Type](field: CaseClassField[q.type, T]): Expr[EndpointInput.Basic[f]] = {
    val name = Expr(field.name)
    '{ path[f]($name)(using ${ summonCodec[String, f, TextPlain](field) }) }
  }

  private def makeFixedPath(segment: String): Expr[EndpointInput.Basic[Unit]] = '{ stringToPath(${ Expr(segment) }) }

  private def makeStatusCodeOutput[f: Type](field: CaseClassField[q.type, T]): Expr[EndpointOutput.Basic[f]] = {
    summon[Type[f]] match {
      case '[StatusCode] => '{ statusCode.asInstanceOf[EndpointOutput.Basic[f]] }
      case _             => report.errorAndAbort(annotationErrorMsg[f, StatusCode]("@statusCode", field))
    }
  }

  private def makeSetCookieOutput[f: Type](field: CaseClassField[q.type, T])(altName: Option[String]): Expr[EndpointOutput.Basic[f]] = {
    val name = Expr(altName.getOrElse(field.name))
    summon[Type[f]] match {
      case '[CookieValueWithMeta] => '{ setCookie($name).asInstanceOf[EndpointOutput.Basic[f]] }
      case _                      => report.errorAndAbort(annotationErrorMsg[f, CookieValueWithMeta]("@setCookie", field))
    }
  }

  private def makeSetCookiesOutput[f: Type](field: CaseClassField[q.type, T]): Expr[EndpointOutput.Basic[f]] = {
    summon[Type[f]] match {
      case '[List[CookieWithMeta]] => '{ setCookies.asInstanceOf[EndpointOutput.Basic[f]] }
      case _                       => report.errorAndAbort(annotationErrorMsg[f, List[CookieWithMeta]]("@setCookies", field))
    }
  }

  private def annotationErrorMsg[f: Type, e: Type](annName: String, field: CaseClassField[q.type, T]) =
    s"Annotation $annName on ${field.name} can be applied only to a field with type ${Type.show[e]}, but got: ${Type.show[f]}"

  // auth inputs
  private def makeBearerAuthInput[f: Type](
      field: CaseClassField[q.type, T],
      schemeName: Option[Term],
      auth: Term
  ): Expr[EndpointInput.Single[f]] =
    setSecuritySchemeName(
      '{
        TapirAuth.bearer(${ auth.asExprOf[EndpointIO.annotations.bearer] }.challenge)(${
          summonCodec[List[String], f, CodecFormat.TextPlain](field)
        })
      },
      schemeName
    )

  private def makeBasicAuthInput[f: Type](
      field: CaseClassField[q.type, T],
      schemeName: Option[Term],
      auth: Term
  ): Expr[EndpointInput.Single[f]] =
    setSecuritySchemeName(
      '{
        TapirAuth.basic(${ auth.asExprOf[EndpointIO.annotations.basic] }.challenge)(${
          summonCodec[List[String], f, CodecFormat.TextPlain](field)
        })
      },
      schemeName
    )

  // schema & auth wrappers
  private def wrapInput[f: Type](field: CaseClassField[q.type, T], input: Expr[EndpointInput.Single[f]]): Expr[EndpointInput.Single[f]] = {
    val input2 = '{ ${ addMetadataFromAnnotations[f](field, input) }.asInstanceOf[EndpointInput.Single[f]] }
    wrapWithApiKey(input2, field.annotation(apikeyAnnotationSymbol), field.annotation(securitySchemeNameAnnotationSymbol))
  }

  private def wrapWithApiKey[f: Type](
      input: Expr[EndpointInput.Single[f]],
      apikey: Option[Term],
      schemeName: Option[Term]
  ): Expr[EndpointInput.Single[f]] =
    apikey
      .map(ak =>
        setSecuritySchemeName(
          '{
            EndpointInput.Auth(
              $input,
              ${ ak.asExprOf[EndpointIO.annotations.apikey] }.challenge,
              EndpointInput.AuthType.ApiKey(),
              EndpointInput.AuthInfo.Empty
            )
          },
          schemeName
        )
      )
      .getOrElse(input)

  private def setSecuritySchemeName[f: Type](
      auth: Expr[EndpointInput.Auth[f, _]],
      schemeName: Option[Term]
  ): Expr[EndpointInput.Single[f]] =
    schemeName
      .map(s => '{ $auth.securitySchemeName(${ s.asExprOf[EndpointIO.annotations.securitySchemeName] }.name) })
      .getOrElse(auth)

  private def addMetadataFromAnnotations[f: Type](
      field: CaseClassField[q.type, T],
      transput: Expr[EndpointTransput[f]]
  ): Expr[EndpointTransput[f]] = {
    val transformations: List[Expr[EndpointTransput[f]] => Expr[EndpointTransput[f]]] = List(
      t =>
        field
          .extractStringArgFromAnnotation(descriptionAnnotationSymbol)
          .map(d => addMetadataToAtom(field, t, '{ i => i.description(${ Expr(d) }) }))
          .getOrElse(t),
      t =>
        field
          .extractTreeFromAnnotation(exampleAnnotationSymbol)
          .map(e => addMetadataToAtom(field, t, '{ i => i.example(${ e.asExprOf[f] }) }))
          .getOrElse(t),
      t =>
        field
          .extractStringArgFromAnnotation(schemaDescriptionAnnotationSymbol)
          .map(d => addMetadataToAtom(field, t, '{ i => i.schema(_.description(${ Expr(d) })) }))
          .getOrElse(t),
      t =>
        field
          .extractTreeFromAnnotation(schemaEncodedExampleAnnotationSymbol)
          .map(e => addMetadataToAtom(field, t, '{ i => i.schema(_.encodedExample(${ e.asExprOf[Any] })) }))
          .getOrElse(t),
      t =>
        field
          .extractFirstTreeArgFromAnnotation(schemaDefaultAnnotationSymbol)
          .map(d => addMetadataToAtom(field, t, '{ i => i.default(${ d.asExprOf[f] }) }))
          .getOrElse(t),
      t =>
        field
          .extractStringArgFromAnnotation(schemaFormatAnnotationSymbol)
          .map(format => addMetadataToAtom(field, t, '{ i => i.schema(_.format(${ Expr(format) })) }))
          .getOrElse(t),
      t =>
        if (field.annotated(schemaDeprecatedAnnotationSymbol)) then addMetadataToAtom(field, t, '{ i => i.deprecated() })
        else t,
      t =>
        field
          .extractTreeFromAnnotation(schemaValidateAnnotationSymbol)
          .map(v => addMetadataToAtom(field, t, '{ i => i.validate(${ v.asExprOf[Validator[f]] }) }))
          .getOrElse(t),
      t =>
        field
          .extractTreeFromAnnotation(customiseAnnotationSymbol)
          .map(f => '{ AnnotationsMacros.customise($t, ${ f.asExprOf[EndpointTransput[_] => EndpointTransput[_]] }) })
          .getOrElse(t)
    )

    transformations.foldLeft(transput)((t, f) => f(t))
  }

  private def addMetadataToAtom[f: Type](
      field: CaseClassField[q.type, T],
      transput: Expr[EndpointTransput[f]],
      f: Expr[EndpointTransput.Atom[f] => Any]
  ): Expr[EndpointTransput[f]] = {
    transput.asTerm.tpe.asType match {
      // TODO: also handle Auth inputs, by adding the description to the nested input
      case '[EndpointTransput.Atom[`f`]] =>
        '{ $f($transput.asInstanceOf[EndpointTransput.Atom[f]]).asInstanceOf[EndpointTransput.Atom[f]] }
      case t =>
        report.errorAndAbort(
          s"Schema metadata can only be added to basic inputs/outputs, but got: ${Type.show(using t)}, on field: ${field.name}"
        )
    }
  }

  // mapping functions
  // A - single arg or tuple, T - target type
  private def mapToTargetFunc[A: Type](inputIdxToFieldIdx: mutable.Map[Int, Int]): Expr[A => T] = {
    if (inputIdxToFieldIdx.size > 1) {
      val fieldIdxToInputIdx = inputIdxToFieldIdx.map(_.swap)
      def ctorArgs(tupleExpr: Expr[A]) = (0 until fieldIdxToInputIdx.size).map { idx =>
        Select.unique(tupleExpr.asTerm, s"_${fieldIdxToInputIdx(idx) + 1}").asExprOf[Any]
      }

      '{ (a: A) => ${ caseClass.instanceFromValues(ctorArgs('a)) } }
    } else {
      '{ (a: A) => ${ caseClass.instanceFromValues('{ List(a) }) } }
    }
  }

  private def mapFromTargetFunc[A: Type](inputIdxToFieldIdx: mutable.Map[Int, Int]): Expr[T => A] = {
    def tupleArgs(tExpr: Expr[T]): Seq[Expr[Any]] = (0 until inputIdxToFieldIdx.size).map { idx =>
      val field = caseClass.fields(inputIdxToFieldIdx(idx))
      Select.unique(tExpr.asTerm, field.name).asExprOf[Any]
    }

    if (inputIdxToFieldIdx.size == 0) {
      '{ (t: T) => ().asInstanceOf[A] }
    } else if (inputIdxToFieldIdx.size > 1) {
      '{ (t: T) => ${ Expr.ofTupleFromSeq(tupleArgs('t)) }.asInstanceOf[A] }
    } else {
      '{ (t: T) => ${ tupleArgs('t).head }.asInstanceOf[A] }
    }
  }
}

// TODO: make private[tapir] once Scala3 compilation is fixed
object AnnotationsMacros:
  // we assume that the customisation function doesn't return a value of a different type
  def customise[X <: EndpointTransput[_]](i: X, f: EndpointTransput[_] => EndpointTransput[_]): X = f(i).asInstanceOf[X]
