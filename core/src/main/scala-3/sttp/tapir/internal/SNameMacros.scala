package sttp.tapir.internal

import scala.quoted.*
import sttp.tapir.SchemaType.*

object SNameMacros {

  inline def typeFullName[T] = ${ typeFullNameImpl[T] }
  private def typeFullNameImpl[T: Type](using q: Quotes) = {
    import q.reflect.*

    def normalizedName(s: Symbol): String = if s.flags.is(Flags.Module) then s.name.stripSuffix("$") else s.name
    def nameChain(sym: Symbol): List[String] =
      if sym.isNoSymbol then List.empty
      else if sym == defn.EmptyPackageClass then List.empty
      else if sym == defn.RootPackage then List.empty
      else if sym == defn.RootClass then List.empty
      else nameChain(sym.owner) :+ normalizedName(sym)

    val tpe = TypeRepr.of[T]

    Expr(nameChain(tpe.typeSymbol).mkString("."))
  }

  def extractTypeArguments(using q: Quotes)(tpe: q.reflect.TypeRepr): List[String] = {
    import q.reflect.*

    def allTypeArguments(tr: TypeRepr): Seq[TypeRepr] = tr match {
      case at: AppliedType => at.args.flatMap(tn2 => tn2 +: allTypeArguments(tn2))
      case _               => List.empty[TypeRepr]
    }

    allTypeArguments(tpe).map(_.typeSymbol.name).toList
  }

}
