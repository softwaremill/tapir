package sttp.tapir.newschema

import sttp.tapir.Schema
import sttp.tapir.generic.Configuration
import sttp.tapir.internal.SchemaAnnotationsMacro

import java.util.concurrent.ConcurrentHashMap
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.ConcurrentMapHasAsScala
import scala.reflect.macros.blackbox

object NewSchemaMacro {

  implicit def gen[T](implicit genericDerivationConfig: Configuration): Schema[T] = macro genImpl[T]

  /*
        val primitives = Set(
        DoubleTpe,
        FloatTpe,
        ShortTpe,
        ByteTpe,
        IntTpe,
        LongTpe,
        CharTpe,
        BooleanTpe,
        UnitTpe
      )

      val isValueClass = genericType <:< AnyValTpe && !primitives.exists(_ =:= genericType)
   */

  private[newschema] val deriveInProgress: scala.collection.mutable.Map[String, Unit] = new ConcurrentHashMap[String, Unit]().asScala

  def genImpl[T: c.WeakTypeTag](c: blackbox.Context)(genericDerivationConfig: c.Expr[Configuration]): c.Expr[Schema[T]] = {
    import c.universe._
    val t = weakTypeOf[T]
    val s = t.typeSymbol

    val encodedNameType = c.weakTypeOf[Schema.annotations.encodedName]

    def doGen(): c.Expr[Schema[T]] = {

      val typeInfo = TypeInfo.forType(t)
      val annotations = Annotations.forSymbol(s)

      val r = withCache(typeInfo, annotations) {
        if (t.typeSymbol.isClass && t.typeSymbol.asClass.isCaseClass) {
          q"""Schema[$t](schemaType = $productSchemaType, name = Some(${typeInfoToSchemaName(typeInfo, annotations)}))"""
        } else {
          c.abort(c.enclosingPosition, s"Only case classes are supported, but got: $t")
        }
      }

      c.Expr[Schema[T]](q"""
        import sttp.tapir.Schema                            
        import sttp.tapir.SchemaType
        import sttp.tapir.FieldName
        $r
      """)
    }

    def productSchemaType: Tree = {
      val fields: List[Symbol] = t.decls
        .collectFirst {
          case m: MethodSymbol if m.isPrimaryConstructor => m
        }
        .get
        .paramLists
        .head

      val productFields = fields.map { field =>
        val name = field.name.decodedName.toString
        val annotations = Annotations.forSymbol(field)
        val encodedName = annotations.encodedName.map(n => q"$n").getOrElse(q"$genericDerivationConfig.toEncodedName($name)")

        val fieldTpe = field.typeSignature
        val fieldSchema = c.inferImplicitValue(appliedType(typeOf[Schema[_]], fieldTpe))
        println("RECURSE " + fieldSchema)
        if (fieldSchema == EmptyTree) {
          c.abort(c.enclosingPosition, s"Cannot find schema for $fieldTpe")
        }
        val enrichedSchema = enrichSchema(fieldSchema, annotations)

        val fieldTermName = field.name.asInstanceOf[TermName]

        q"""SchemaType.SProductField(FieldName($name, $encodedName), $enrichedSchema, obj => Some(obj.$fieldTermName))"""
      }

      q"""SchemaType.SProduct($productFields)"""
    }

    def typeInfoToSchemaName(typeInfo: TypeInfo, annotations: Annotations): Tree = {
      def allTypeArguments(tn: TypeInfo): Seq[TypeInfo] = tn.typeArguments.flatMap(tn2 => tn2 +: allTypeArguments(tn2))

      annotations.encodedName match {
        case Some(value) => q"Schema.SName($value, Nil)"
        case None        => q"Schema.SName(${typeInfo.full}, ${allTypeArguments(typeInfo).map(_.short).toList})"
      }
    }

    def enrichSchema(schema: Tree, annotations: Annotations): Tree = {
      q"${SchemaAnnotationsMacro.forAnnotations(c)(annotations.all)}.enrich($schema)"
    }

    // from magnolia

    case class TypeInfo(owner: String, short: String, typeArguments: Seq[TypeInfo]) {
      val full: String = s"$owner.$short"
    }

    object TypeInfo {
      def forType(tpe: Type): TypeInfo = {
        val tpe1 = tpe.dealias
        val symbol = tpe1.typeSymbol
        val typeArgNames = for (typeArg <- tpe1.typeArgs) yield forType(typeArg)
        TypeInfo(symbol.owner.fullName, symbol.name.decodedName.toString, typeArgNames)
      }
    }

    class Annotations(topLevel: List[Tree], inherited: List[Tree]) {
      lazy val all: List[Tree] =
        // skip inherited annotations if defined at the top-level
        topLevel ++ inherited.filterNot(i => topLevel.exists(t => t.tpe <:< i.tpe))

      def encodedName: Option[String] =
        all.collectFirst {
          case a if a.tpe <:< encodedNameType =>
            a.children.tail match {
              case List(Literal(Constant(str: String))) => str
              case _ => throw new IllegalStateException(s"Cannot extract annotation argument from: ${c.universe.showRaw(a)}")
            }
        }
    }

    object Annotations {
      private val JavaAnnotationTpe = typeOf[java.lang.annotation.Annotation]
      private def annotationTrees(annotations: List[Annotation]): List[Tree] =
        annotations.collect {
          // filtering out NoType annotations due to https://github.com/scala/bug/issues/12536
          case annotation if !(annotation.tree.tpe <:< JavaAnnotationTpe) && !(annotation.tree.tpe <:< NoType) =>
            annotation.tree
        }

      def forSymbol(symbol: Symbol): Annotations = {
        @tailrec
        def fromBaseClassesMembers(owner: Symbol): List[Annotation] =
          if (owner.isClass) {
            val baseClasses = owner.asClass.baseClasses
              .filterNot(bc => bc.fullName.contains("java.lang.Object") || bc.fullName.startsWith("scala."))

            val fromMembers = baseClasses
              .flatMap(_.asType.toType.members)
              .filter(s =>
                (symbol, s) match {
                  case (m1: MethodSymbol, m2: MethodSymbol) if m1.name == m2.name && m1.paramLists.size == m2.paramLists.size => true
                  case (t1: TermSymbol, t2: TermSymbol) if t1.name == t2.name                                                 => true
                  case _                                                                                                      => false
                }
              )
              .flatMap(_.annotations)

            val fromConstructorParameters = baseClasses
              .flatMap {
                case c: ClassSymbol =>
                  c.primaryConstructor match {
                    case m: MethodSymbol =>
                      m.paramLists.flatten
                        .filter { s =>
                          (symbol, s) match {
                            case (t1: TermSymbol, t2: TermSymbol) if t1.name == t2.name => true
                            case _                                                      => false
                          }
                        }
                        .flatMap(_.annotations)
                    case _ => List.empty
                  }
                case _ => List.empty
              }

            fromMembers ++ fromConstructorParameters
          } else fromBaseClassesMembers(owner.owner)

        def fromBaseClasses(): List[Annotation] =
          symbol.asClass.baseClasses.collect { case s if s.name != symbol.name => s.annotations }.flatten

        val inherited = if (symbol.isClass) fromBaseClasses() else fromBaseClassesMembers(symbol.owner)

        new Annotations(annotationTrees(symbol.annotations), annotationTrees(inherited))
      }
    }

    //

    /** To avoid recursive loops, we keep track of the fully qualified names of types for which derivation is in progress using a global
      * mutable Set.
      */
    def withCache(typeInfo: TypeInfo, annotations: Annotations)(f: => Tree): Tree = {
      val cacheKey = typeInfo.full
      println("IN " + cacheKey)
      if (deriveInProgress.contains(cacheKey))
        q"""Schema(SRef(${typeInfoToSchemaName(typeInfo, annotations)}))"""
      else
        try {
          deriveInProgress.put(cacheKey, ())
          val schema = f
          schema
        } finally {
          println("OUT")
          deriveInProgress.remove(cacheKey)
        }
    }

    //

    doGen()
  }
}
