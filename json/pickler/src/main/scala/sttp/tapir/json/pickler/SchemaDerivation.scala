package sttp.tapir.json.pickler

import sttp.tapir.SchemaType.{SCoproduct, SProduct, SProductField, SRef, SchemaWithValue}
import sttp.tapir.generic.Configuration
import sttp.tapir.{FieldName, Schema, SchemaType, Validator}

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.ConcurrentMapHasAsScala
import scala.quoted.*
import scala.reflect.ClassTag

private[pickler] object SchemaDerivation:
  private[pickler] val deriveInProgress: scala.collection.mutable.Map[String, Unit] = new ConcurrentHashMap[String, Unit]().asScala

  inline def productSchema[T, TFields <: Tuple](
      genericDerivationConfig: Configuration,
      childSchemas: Tuple.Map[TFields, Schema]
  ): Schema[T] =
    ${ productSchemaImpl('genericDerivationConfig, 'childSchemas) }

  def productSchemaImpl[T: Type, TFields <: Tuple](
      genericDerivationConfig: Expr[Configuration],
      childSchemas: Expr[Tuple.Map[TFields, Schema]]
  )(using Quotes, Type[TFields]): Expr[Schema[T]] =
    new SchemaDerivation(genericDerivationConfig).productSchemaImpl(childSchemas)

  inline def coproductSchema[T, TFields <: Tuple](
      genericDerivationConfig: Configuration,
      childSchemas: Tuple.Map[TFields, Schema]
  ): Schema[T] =
    ${ coproductSchemaImpl('genericDerivationConfig, 'childSchemas) }

  def coproductSchemaImpl[T: Type, TFields <: Tuple](
      genericDerivationConfig: Expr[Configuration],
      childSchemas: Expr[Tuple.Map[TFields, Schema]]
  )(using Quotes, Type[TFields]): Expr[Schema[T]] =
    new SchemaDerivation(genericDerivationConfig).coproductSchemaImpl(childSchemas)

private class SchemaDerivation(genericDerivationConfig: Expr[Configuration])(using Quotes):

  import quotes.reflect.*

  // products

  private def productSchemaImpl[T: Type, TFields <: Tuple](
      childSchemas: Expr[Tuple.Map[TFields, Schema]]
  )(using Quotes, Type[TFields]): Expr[Schema[T]] =
    val tpe = TypeRepr.of[T]
    val typeInfo = TypeInfo.forType(tpe)
    val annotations = Annotations.onType(tpe)
    val schema = withCache(typeInfo, annotations) {
      '{ Schema[T](schemaType = ${ productSchemaType(childSchemas) }, name = Some(${ typeNameToSchemaName(typeInfo, annotations) })) }
    }
    enrichSchema(schema, annotations)

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

  // coproducts

  private def coproductSchemaImpl[T: Type, TFields <: Tuple](
      childSchemas: Expr[Tuple.Map[TFields, Schema]]
  )(using Quotes, Type[TFields]): Expr[Schema[T]] = {
    val tpe = TypeRepr.of[T]
    val typeInfo = TypeInfo.forType(tpe)
    val annotations = Annotations.onType(tpe)
    val schema = withCache(typeInfo, annotations) {
      '{
        Schema[T](
          schemaType = ${ coproductSchemaType[T, TFields](tpe, childSchemas) },
          name = Some(${ typeNameToSchemaName(typeInfo, annotations) })
        )
      }
    }
    enrichSchema(schema, annotations)
  }

  private def coproductSchemaType[T: Type, TFields <: Tuple](tpe: TypeRepr, childSchemas: Expr[Tuple.Map[TFields, Schema]])(using
      Type[TFields]
  ): Expr[SCoproduct[T]] =

    val childSchemasArray = '{ $childSchemas.toArray }
    val childNameTagSchemaExprList = tpe.typeSymbol.children.zipWithIndex.map { case (childSymbol, i) =>
      // see https://github.com/lampepfl/dotty/discussions/15157#discussioncomment-2728606
      val childTpe =
        if childSymbol.flags.is(Flags.Module) then TypeIdent(childSymbol.owner).tpe.memberType(childSymbol) else TypeIdent(childSymbol).tpe
      childTpe.asType match
        case '[c] =>
          val childSchema: Expr[Schema[c]] = '{ $childSchemasArray(${ Expr(i) }).asInstanceOf[Schema[c]] }
          val classTag = summonClassTag[c]
          val name = typeNameToSchemaName(TypeInfo.forType(childTpe), Annotations.onType(childTpe))
          '{ ($name, $classTag, $childSchema) }
    }

    '{
      val childTagAndSchema: List[(Schema.SName, ClassTag[_], Schema[_])] = ${ Expr.ofList(childNameTagSchemaExprList) }
      val baseCoproduct = SCoproduct(childTagAndSchema.map(_._3), None)((t: T) =>
        childTagAndSchema
          .map((_, ct, s) => (ct.unapply(t), s))
          .collectFirst { case (Some(v), s) => SchemaWithValue(s.asInstanceOf[Schema[Any]], v) }
      )
      $genericDerivationConfig.discriminator match {
        case Some(d) =>
          val discriminatorMapping: Map[String, SRef[_]] = childTagAndSchema
            .map(_._1)
            .map(schemaName => $genericDerivationConfig.toDiscriminatorValue(schemaName) -> SRef(schemaName))
            .toMap
          baseCoproduct.addDiscriminatorField(FieldName(d), discriminatorMapping = discriminatorMapping)
        case None =>
          baseCoproduct
      }
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
    import SchemaDerivation.deriveInProgress
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
        case '{ $ann: Schema.annotations.description }     => '{ $schema.description($ann.text) }
        case '{ $ann: Schema.annotations.encodedExample }  => '{ $schema.encodedExample($ann.example) }
        case '{ $ann: Schema.annotations.default[? <: X] } => '{ $schema.default($ann.default, $ann.encoded) }
        case '{ $ann: Schema.annotations.validate[X] }     => '{ $schema.validate($ann.v) }
        case '{ $ann: Schema.annotations.validateEach[?] } =>
          '{ $schema.modifyUnsafe[X](Schema.ModifyCollectionElements)((_: Schema[X]).validate($ann.v.asInstanceOf[Validator[X]])) }
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
          .filterNot(isObjectOrScalaOrJava)
          .collect {
            case s if s != tpe.typeSymbol => s.annotations
          } // skip self
          .flatten
          .filter(filterAnnotation)
      Annotations(topLevel, inherited)

    def onParams(tpe: TypeRepr): Map[String, Annotations] =
      def paramAnns: List[(String, List[Term])] = groupByParamName {
        (fromConstructor(tpe.typeSymbol) ++ fromDeclarations(tpe.typeSymbol, includeMethods = false))
          .filter { case (_, anns) => anns.nonEmpty }
      }

      def inheritedParamAnns: List[(String, List[Term])] =
        groupByParamName {
          tpe.baseClasses
            .filterNot(isObjectOrScalaOrJava)
            .collect {
              case s if s != tpe.typeSymbol =>
                (fromConstructor(s) ++ fromDeclarations(s, includeMethods = true)).filter { case (_, anns) =>
                  anns.nonEmpty
                }
            }
            .flatten
        }

      def fromConstructor(from: Symbol): List[(String, List[Term])] =
        from.primaryConstructor.paramSymss.flatten.map { field => field.name -> field.annotations.filter(filterAnnotation) }

      def fromDeclarations(from: Symbol, includeMethods: Boolean): List[(String, List[Term])] =
        from.declarations.collect {
          // using TypeTest
          case field: Symbol if (field.tree match { case _: ValDef => true; case _: DefDef if includeMethods => true; case _ => false }) =>
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

    private def isObjectOrScalaOrJava(bc: Symbol) =
      bc.name.contains("java.lang.Object") || bc.fullName.startsWith("scala.") || bc.fullName.startsWith("java.")

    private def filterAnnotation(a: Term): Boolean =
      a.tpe.typeSymbol.maybeOwner.isNoSymbol ||
        a.tpe.typeSymbol.owner.fullName != "scala.annotation.internal"
