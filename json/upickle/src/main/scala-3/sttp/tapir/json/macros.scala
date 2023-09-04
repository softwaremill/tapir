package sttp.tapir.json.macros

import scala.quoted.*
import deriving.*, compiletime.*
import scala.reflect.ClassTag
import _root_.upickle.implicits.*
import _root_.upickle.implicits.{macros => uMacros}
import sttp.tapir.Schema
import sttp.tapir.SchemaType.SProduct
import sttp.tapir.generic.Configuration
import sttp.tapir.Schema
import sttp.tapir.SchemaType
import scala.reflect.TypeTest
import sttp.tapir.SchemaType.SProductField
import sttp.tapir.SchemaType.SProduct
import sttp.tapir.FieldName
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.ConcurrentMapHasAsScala
import sttp.tapir.SchemaType.SRef
import sttp.tapir.SchemaType.SCoproduct
import scala.reflect.ClassTag
import sttp.tapir.SchemaType.SchemaWithValue
import sttp.tapir.json.generic

type IsInt[A <: Int] = A

inline def writeSnippets[R, T](
    inline sProduct: SProduct[T],
    inline thisOuter: upickle.core.Types with upickle.implicits.MacrosCommon,
    inline self: upickle.implicits.CaseClassReadWriters#CaseClassWriter[T],
    inline v: T,
    inline ctx: _root_.upickle.core.ObjVisitor[_, R],
    childWriters: List[Any]
): Unit =
  ${ writeSnippetsImpl[R, T]('sProduct, 'thisOuter, 'self, 'v, 'ctx, 'childWriters) }

def writeSnippetsImpl[R, T](
    sProduct: Expr[SProduct[T]],
    thisOuter: Expr[upickle.core.Types with upickle.implicits.MacrosCommon],
    self: Expr[upickle.implicits.CaseClassReadWriters#CaseClassWriter[T]],
    v: Expr[T],
    ctx: Expr[_root_.upickle.core.ObjVisitor[_, R]],
    childWriters: Expr[List[?]]
)(using Quotes, Type[T], Type[R]): Expr[Unit] =

  import quotes.reflect.*

  Expr.block(
    for (((rawLabel, label), i) <- uMacros.fieldLabelsImpl0[T].zipWithIndex) yield {
      val tpe0 = TypeRepr.of[T].memberType(rawLabel).asType
      tpe0 match
        case '[tpe] =>
          val defaults = uMacros.getDefaultParamsImpl0[T]
          Literal(IntConstant(i)).tpe.asType match
            case '[IsInt[index]] =>
              val encodedName = '{ ${ sProduct }.fields(${ Expr(i) }).name.encodedName }
              val select = Select.unique(v.asTerm, rawLabel.name).asExprOf[Any]
              // val encodedNameExpr = '{ ${schema} match { case } }

              val snippet = '{
                ${ self }.writeSnippetMappedName[R, tpe](
                  ${ ctx },
                  ${ encodedName },
                  ${ childWriters }(${ Expr(i) }),
                  ${ select }
                )
              }
              if (!defaults.contains(label)) snippet
              else '{ if (${ thisOuter }.serializeDefaults || ${ select } != ${ defaults(label) }) $snippet }

    },
    '{ () }
  )

object SchemaDerivation2:
  private[macros] val deriveInProgress: scala.collection.mutable.Map[String, Unit] = new ConcurrentHashMap[String, Unit]().asScala

  inline def productSchema[T, TFields <: Tuple](
      genericDerivationConfig: Configuration,
      childSchemas: Tuple.Map[TFields, Schema]
  ): Schema[T] =
    ${ SchemaDerivation2.productSchemaImpl('genericDerivationConfig, 'childSchemas) }

  def productSchemaImpl[T: Type, TFields <: Tuple](
      genericDerivationConfig: Expr[Configuration],
      childSchemas: Expr[Tuple.Map[TFields, Schema]]
  )(using Quotes, Type[TFields]): Expr[Schema[T]] =
    new SchemaDerivation2(genericDerivationConfig).productSchemaImpl(childSchemas)

private class SchemaDerivation2(genericDerivationConfig: Expr[Configuration])(using Quotes):

  import quotes.reflect.*

  private def productSchemaImpl[T: Type, TFields <: Tuple](
      childSchemas: Expr[Tuple.Map[TFields, Schema]]
  )(using Quotes, Type[TFields]): Expr[Schema[T]] =
    val tpe = TypeRepr.of[T]
    val typeInfo = TypeInfo.forType(tpe)
    val annotations = Annotations.onType(tpe)
    '{ Schema[T](schemaType = ${ productSchemaType(childSchemas) }, name = Some(${ typeNameToSchemaName(typeInfo, annotations) })) }

  private def productSchemaType[T: Type, TFields <: Tuple](
      childSchemas: Expr[Tuple.Map[TFields, Schema]]
  )(using Quotes, Type[TFields]): Expr[SProduct[T]] =
    val tpe: TypeRepr = TypeRepr.of[T]
    val fieldsAnnotations = Annotations.onParams(tpe)
    val childSchemasArray = '{ $childSchemas.toArray }
    '{
      SProduct(${
        Expr.ofList(tpe.typeSymbol.caseFields.zipWithIndex.map { case (fieldSymbol, i) =>
          val name = Expr(fieldSymbol.name)

          val fieldTpe = tpe.memberType(fieldSymbol)
          val fieldAnnotations = fieldsAnnotations.getOrElse(fieldSymbol.name, Annotations.Empty)

          val encodedName = fieldAnnotations.encodedName.getOrElse('{ $genericDerivationConfig.toEncodedName($name) })

          fieldTpe.asType match
            case '[f] =>
              val fieldSchema: Expr[Schema[f]] = '{ $childSchemasArray(${ Expr(i) }).asInstanceOf[Schema[f]] }
              val enrichedFieldSchema = enrichSchema(fieldSchema, fieldAnnotations)

              '{
                SProductField(
                  FieldName($name, $encodedName),
                  $enrichedFieldSchema,
                  obj => Some(${ Select('{ obj }.asTerm, fieldSymbol).asExprOf[f] })
                )
              }
        })
      })
    }

  // helper methods

  private def summonClassTag[T: Type]: Expr[ClassTag[T]] = Expr.summon[ClassTag[T]] match
    case None     => report.errorAndAbort(s"Cannot find a ClassTag for ${Type.show[T]}!")
    case Some(ct) => ct

  private def summonChildSchema[T: Type]: Expr[Schema[T]] = Expr.summon[Schema[T]] match
    case None    => report.errorAndAbort(s"Cannot find schema for ${Type.show[T]}!")
    case Some(s) => s

  /** To avoid recursive loops, we keep track of the fully qualified names of types for which derivation is in progress using a global
    * mutable Set.
    */
  private def withCache[T: Type](typeInfo: TypeInfo, annotations: Annotations)(f: => Expr[Schema[T]]): Expr[Schema[T]] =
    import SchemaDerivation2.deriveInProgress
    val cacheKey = typeInfo.full
    if deriveInProgress.contains(cacheKey) then '{ Schema[T](SRef(${ typeNameToSchemaName(typeInfo, annotations) })) }
    else
      try
        deriveInProgress.put(cacheKey, ())
        val schema = f
        schema
      finally deriveInProgress.remove(cacheKey)

  private def typeNameToSchemaName(typeInfo: TypeInfo, annotations: Annotations): Expr[Schema.SName] =
    val encodedName: Option[Expr[String]] = annotations.encodedName

    encodedName match
      case None =>
        def allTypeArguments(tn: TypeInfo): Seq[TypeInfo] = tn.typeParams.toList.flatMap(tn2 => tn2 +: allTypeArguments(tn2))
        '{ Schema.SName(${ Expr(typeInfo.full) }, ${ Expr.ofList(allTypeArguments(typeInfo).map(_.short).toList.map(Expr(_))) }) }
      case Some(en) =>
        '{ Schema.SName($en, Nil) }

  private def enrichSchema[X: Type](schema: Expr[Schema[X]], annotations: Annotations): Expr[Schema[X]] =
    annotations.all.foldLeft(schema) { (schema, annTerm) =>
      annTerm.asExpr match
        case '{ $ann: Schema.annotations.description }    => '{ $schema.description($ann.text) }
        case '{ $ann: Schema.annotations.encodedExample } => '{ $schema.encodedExample($ann.example) }
        case '{ $ann: Schema.annotations.default[X] }     => '{ $schema.default($ann.default, $ann.encoded) }
        case '{ $ann: Schema.annotations.validate[X] }    => '{ $schema.validate($ann.v) }
        case '{ $ann: Schema.annotations.validateEach[X] } =>
          '{ $schema.modifyUnsafe(Schema.ModifyCollectionElements)((_: Schema[X]).validate($ann.v)) }
        case '{ $ann: Schema.annotations.format }     => '{ $schema.format($ann.format) }
        case '{ $ann: Schema.annotations.deprecated } => '{ $schema.deprecated(true) }
        case '{ $ann: Schema.annotations.customise }  => '{ $ann.f($schema).asInstanceOf[Schema[X]] }
        case _                                        => schema
    }

  // helper classes

  private case class TypeInfo(owner: String, short: String, typeParams: Iterable[TypeInfo]):
    def full: String = s"$owner.$short"

  private object TypeInfo:
    def forType(tpe: TypeRepr): TypeInfo =
      def normalizedName(s: Symbol): String =
        if s.flags.is(Flags.Module) then s.name.stripSuffix("$") else s.name
      def name(tpe: TypeRepr): String = tpe match
        case TermRef(typeRepr, name) if tpe.typeSymbol.flags.is(Flags.Module) => name.stripSuffix("$")
        case TermRef(typeRepr, name)                                          => name
        case _                                                                => normalizedName(tpe.typeSymbol)

      def ownerNameChain(sym: Symbol): List[String] =
        if sym.isNoSymbol then List.empty
        else if sym == defn.EmptyPackageClass then List.empty
        else if sym == defn.RootPackage then List.empty
        else if sym == defn.RootClass then List.empty
        else ownerNameChain(sym.owner) :+ normalizedName(sym)

      def owner(tpe: TypeRepr): String = ownerNameChain(tpe.typeSymbol.maybeOwner).mkString(".")

      tpe match
        case AppliedType(tpe, args) => TypeInfo(owner(tpe), name(tpe), args.map(forType))
        case _                      => TypeInfo(owner(tpe), name(tpe), Nil)

  //
  private class Annotations(topLevel: List[Term], inherited: List[Term]):
    lazy val all: List[Term] =
      // skip inherited annotations if defined at the top-level
      topLevel ++ inherited.filterNot(i => topLevel.exists(t => t.tpe <:< i.tpe))

    def encodedName: Option[Expr[String]] = all
      .map(_.asExpr)
      .collectFirst { case '{ $en: Schema.annotations.encodedName } => en }
      .map(en => '{ $en.name })

  private object Annotations:
    val Empty: Annotations = Annotations(Nil, Nil)

    def onType(tpe: TypeRepr): Annotations =
      val topLevel: List[Term] = tpe.typeSymbol.annotations.filter(filterAnnotation)
      val inherited: List[Term] =
        tpe.baseClasses
          .filterNot(isObjectOrScala)
          .collect {
            case s if s != tpe.typeSymbol => s.annotations
          } // skip self
          .flatten
          .filter(filterAnnotation)
      Annotations(topLevel, inherited)

    def onParams(tpe: TypeRepr): Map[String, Annotations] =
      def paramAnns: List[(String, List[Term])] = groupByParamName {
        (fromConstructor(tpe.typeSymbol) ++ fromDeclarations(tpe.typeSymbol))
          .filter { case (_, anns) => anns.nonEmpty }
      }

      def inheritedParamAnns: List[(String, List[Term])] =
        groupByParamName {
          tpe.baseClasses
            .filterNot(isObjectOrScala)
            .collect {
              case s if s != tpe.typeSymbol =>
                (fromConstructor(s) ++ fromDeclarations(s)).filter { case (_, anns) =>
                  anns.nonEmpty
                }
            }
            .flatten
        }

      def fromConstructor(from: Symbol): List[(String, List[Term])] =
        from.primaryConstructor.paramSymss.flatten.map { field => field.name -> field.annotations.filter(filterAnnotation) }

      def fromDeclarations(from: Symbol): List[(String, List[Term])] =
        from.declarations.collect {
          // using TypeTest
          case field: Symbol if (field.tree match { case _: ValDef => true; case _ => false }) =>
            field.name -> field.annotations.filter(filterAnnotation)
        }

      def groupByParamName(anns: List[(String, List[Term])]) =
        anns
          .groupBy { case (name, _) => name }
          .toList
          .map { case (name, l) => name -> l.flatMap(_._2) }

      val topLevel = paramAnns.toMap
      val inherited = inheritedParamAnns.toMap
      val params = topLevel.keySet ++ inherited.keySet
      params.map(p => p -> Annotations(topLevel.getOrElse(p, Nil), inherited.getOrElse(p, Nil))).toMap

    private def isObjectOrScala(bc: Symbol) =
      bc.name.contains("java.lang.Object") || bc.fullName.startsWith("scala.")

    private def filterAnnotation(a: Term): Boolean =
      a.tpe.typeSymbol.maybeOwner.isNoSymbol ||
        a.tpe.typeSymbol.owner.fullName != "scala.annotation.internal"

transparent inline def isScalaEnum[X]: Boolean = inline compiletime.erasedValue[X] match
  case _: Null         => false
  case _: Nothing      => false
  case _: reflect.Enum => true
  case _               => false
