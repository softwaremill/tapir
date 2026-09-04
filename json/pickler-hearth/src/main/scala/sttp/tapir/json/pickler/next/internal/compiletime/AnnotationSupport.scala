package sttp.tapir.json.pickler.next.internal.compiletime

import hearth.MacroCommons
import hearth.std.*

/** Cross-platform access to the annotations the tapir `Schema` derivation cares about.
  *
  * Hearth's `Type#annotations` / `Parameter#annotations` already abstract over the Scala 2 and Scala 3 reflection
  * APIs, so nothing here is platform-specific. They are lowered to [[UntypedExpr]] so that the caller can re-type them
  * as `Any` and splice them into a `List[Any]` that the runtime helpers fold over — which keeps annotation *matching*
  * out of the macro entirely.
  */
trait AnnotationSupport { this: MacroCommons & StdExtensions =>

  /** Annotations on a constructor parameter, including those inherited from a member of the same name declared on a
    * base class or trait.
    *
    * Inheritance matters because tapir users routinely document a hierarchy once, on the parent:
    * {{{
    * sealed trait Pet { @description("name") def name: String }
    * case class Dog(name: String, @description("dog food") dogFood: String) extends Pet
    * }}}
    * `Dog.name` must pick up `@description("name")`. Hearth's `Parameter#annotations` only reports annotations written
    * on the parameter itself, so the base classes are walked explicitly.
    *
    * An annotation declared on the parameter wins over an inherited one of the same type, which is what lets a
    * subclass override a parent's `@description`.
    */
  protected def allParamAnnotations[A: Type](param: Parameter, memberName: String): List[UntypedExpr] =
    withInheritanceApplied(param.annotations, inheritedMemberAnnotations[A](memberName))

  /** Annotations on a type, including those inherited from its base classes.
    *
    * Note that `@encodedName` is deliberately *not* consumed from this list when building an `SName` — see
    * `SchemaDerivation.sNameExpr`. A parent's `@encodedName` must not rename its subtypes.
    */
  protected def allTypeAnnotations[A: Type]: List[UntypedExpr] =
    withInheritanceApplied(
      Type[A].annotations,
      baseClassesOf[A].flatMap { base =>
        import base.Underlying as Base
        Type[Base].annotations
      }
    )

  /** Annotations on same-named members of every base class, in linearization order. */
  private def inheritedMemberAnnotations[A: Type](memberName: String): List[Expr_??] =
    baseClassesOf[A].flatMap { base =>
      import base.Underlying as Base
      Type[Base].unsortedMethods.filter(_.name == memberName).flatMap(_.annotations)
    }

  /** Base classes of `A`, excluding `A` itself and the universal/`scala.*` ancestors that carry nothing useful. */
  private def baseClassesOf[A: Type]: List[??] =
    Type[A].baseClasses.filterNot { base =>
      import base.Underlying as Base
      val name = Type[Base].plainPrint
      (Base =:= Type[A]) || name.startsWith("scala.") || name == "java.lang.Object" || name == "java.io.Serializable"
    }

  /** Concatenate own and inherited annotations, dropping inherited ones already present (by type) on the member. */
  private def withInheritanceApplied(own: List[Expr_??], inherited: List[Expr_??]): List[UntypedExpr] =
    (own ++ inherited.filterNot { i =>
      own.exists { o =>
        import o.Underlying as Own, i.Underlying as Inherited
        Type[Own] <:< Type[Inherited]
      }
    }).map(_.asUntyped)

  /** The string argument of a single-string annotation, when it is a literal.
    *
    * Used for `@encodedName` on a type, which has to be known at compile time because it replaces the `SName` that
    * other parts of the derivation (notably the discriminator mapping) are built from.
    */
  protected def literalStringArg[Ann](annotation: Expr[Ann]): Option[String] =
    Annotations.decodedConstructorArguments(annotation).getOrElse(Nil) match {
      case List(Right(value: String)) => Some(value)
      case _                          => None
    }
}
