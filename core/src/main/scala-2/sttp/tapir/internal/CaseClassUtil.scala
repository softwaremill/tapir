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

  lazy val companion: Ident = Ident(TermName(t.typeSymbol.name.decodedName.toString))

  lazy val instanceFromValues: Tree = if (fields.size == 1) {
    q"$companion.apply(values.head.asInstanceOf[${fields.head.typeSignature}])"
  } else {
    q"($companion.apply _).tupled.asInstanceOf[Any => $t].apply(sttp.tapir.internal.SeqToParams(values))"
  }

  lazy val schema: Tree = c.typecheck(q"_root_.scala.Predef.implicitly[_root_.sttp.tapir.Schema[$t]]")

  lazy val classSymbol: ClassSymbol = t.typeSymbol.asClass
  lazy val className: TermName = classSymbol.asType.name.toTermName

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
            // before Scala 3.8.1, the above test for a default value worked; now we need this additional condition
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
