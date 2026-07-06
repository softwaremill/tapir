package sttp.tapir.internal

import scala.reflect.macros.blackbox

private[tapir] class CaseClassUtil[C <: blackbox.Context, T: C#WeakTypeTag](val c: C, name: String) {
  import c.universe._

  val t: Type = weakTypeOf[T]
  if (!t.typeSymbol.isClass || !t.typeSymbol.asClass.isCaseClass) {
    c.error(c.enclosingPosition, s"${name.capitalize} can only be generated for a case class, but got: ${t.typeSymbol.fullName}.")
  }

  lazy val fields: List[Symbol] = t.decls
    .collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }
    .get
    .paramLists
    .head

  // the reference to the companion object must be qualified with the case class's prefix, as a bare-name identifier
  // doesn't resolve for classes nested in objects or classes (see: https://github.com/softwaremill/tapir/issues/4354);
  // local classes have no prefix, but there a bare name is in scope
  lazy val companion: Tree = {
    val name = TermName(t.typeSymbol.name.decodedName.toString)
    t.dealias match {
      case TypeRef(pre, _, _) if pre != NoPrefix => Select(c.internal.gen.mkAttributedQualifier(pre), name)
      case _                                     => Ident(name)
    }
  }

  // apply is called with explicit arguments instead of eta-expansion (`(apply _).tupled`), as the latter fails to
  // compile when the companion defines additional apply overloads
  lazy val instanceFromValues: Tree = {
    val args = fields.zipWithIndex.map { case (field, i) => q"values($i).asInstanceOf[${field.typeSignature}]" }
    q"$companion.apply(..$args)"
  }

  lazy val schema: Tree = c.typecheck(q"_root_.scala.Predef.implicitly[_root_.sttp.tapir.Schema[$t]]")

  lazy val classSymbol: ClassSymbol = t.typeSymbol.asClass

  def annotated(field: Symbol, annotationType: c.Type): Boolean =
    findAnnotation(field, annotationType).isDefined

  def findAnnotation(field: Symbol, annotationType: c.Type): Option[Annotation] =
    field.annotations.find(_.tree.tpe <:< annotationType)

  def extractStringArgFromAnnotation(field: Symbol, annotationType: c.Type): Option[String] = {
    // https://stackoverflow.com/questions/20908671/scala-macros-how-to-read-an-annotation-object
    field.annotations.collectFirst {
      case a if a.tree.tpe <:< annotationType =>
        a.tree.children.tail match {
          case List(Literal(Constant(str: String))) => str
          case _ => throw new IllegalStateException(s"Cannot extract annotation argument from: ${c.universe.showRaw(a.tree)}")
        }
    }
  }

  /** Assumes the default value for the argument is "", in which case returns a `Some(None)`. */
  def extractOptStringArgFromAnnotation(field: Symbol, annotationType: c.Type): Option[Option[String]] =
    field.annotations.collectFirst {
      case a if a.tree.tpe <:< annotationType =>
        a.tree.children.tail match {
          case List(Select(_, name @ TermName(_))) if name.decodedName.toString.startsWith("<init>$default") =>
            None
          case List(Literal(Constant(str: String))) =>
            // before Scala 2.13.18, the above test for a default value worked; now we need this additional condition
            if (str == "") None else Some(str)
          case _ => throw new IllegalStateException(s"Cannot extract annotation argument from: ${c.universe.showRaw(a.tree)}")
        }
    }

  def extractTreeFromAnnotation(field: Symbol, annotationType: c.Type): Option[Tree] = {
    // https://stackoverflow.com/questions/20908671/scala-macros-how-to-read-an-annotation-object
    field.annotations.collectFirst {
      case a if a.tree.tpe <:< annotationType =>
        a.tree.children.tail match {
          case List(t) => t
          case _       => throw new IllegalStateException(s"Cannot extract annotation argument from: ${c.universe.showRaw(a.tree)}")
        }
    }
  }

  def extractFirstTreeArgFromAnnotation(field: Symbol, annotationType: c.Type): Option[Tree] = {
    field.annotations.collectFirst {
      case a if a.tree.tpe <:< annotationType =>
        a.tree.children.tail match {
          case List(t, _*) => t
          case _           => throw new IllegalStateException(s"Cannot extract annotation argument from: ${c.universe.showRaw(a.tree)}")
        }
    }
  }
}
