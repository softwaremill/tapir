package sttp.tapir.internal

import scala.reflect.macros.blackbox

private[tapir] object MapToMacro {
  def generateMapTo[THIS_TYPE[_], T: c.WeakTypeTag, CASE_CLASS: c.WeakTypeTag](c: blackbox.Context): c.Expr[THIS_TYPE[CASE_CLASS]] =
    c.Expr[THIS_TYPE[CASE_CLASS]](generateDelegateMap[T, CASE_CLASS](c)("map"))

  def generateMapSecurityInTo[RESULT, T: c.WeakTypeTag, CASE_CLASS: c.WeakTypeTag](c: blackbox.Context): c.Expr[RESULT] =
    c.Expr[RESULT](generateDelegateMap[T, CASE_CLASS](c)("mapSecurityIn"))

  def generateMapInTo[RESULT, T: c.WeakTypeTag, CASE_CLASS: c.WeakTypeTag](c: blackbox.Context): c.Expr[RESULT] =
    c.Expr[RESULT](generateDelegateMap[T, CASE_CLASS](c)("mapIn"))

  def generateMapErrorOutTo[RESULT, T: c.WeakTypeTag, CASE_CLASS: c.WeakTypeTag](c: blackbox.Context): c.Expr[RESULT] =
    c.Expr[RESULT](generateDelegateMap[T, CASE_CLASS](c)("mapErrorOut"))

  def generateMapOutTo[RESULT, T: c.WeakTypeTag, CASE_CLASS: c.WeakTypeTag](c: blackbox.Context): c.Expr[RESULT] =
    c.Expr[RESULT](generateDelegateMap[T, CASE_CLASS](c)("mapOut"))

  private def generateDelegateMap[T: c.WeakTypeTag, CASE_CLASS: c.WeakTypeTag](
      c: blackbox.Context
  )(delegateTo: String): c.Tree = {
    import c.universe._
    val to = MapToMacro.tupleToCaseClass[T, CASE_CLASS](c)
    val from = MapToMacro.tupleFromCaseClass[T, CASE_CLASS](c)

    q"${c.prefix}.${TermName(delegateTo)}($to)($from)"
  }

  private def tupleToCaseClass[TUPLE: c.WeakTypeTag, CASE_CLASS: c.WeakTypeTag](c: blackbox.Context): c.Tree = {
    import c.universe._

    val caseClassUtil = new CaseClassUtil[c.type, CASE_CLASS](c, "mapTo mapping")
    val tupleType = weakTypeOf[TUPLE]
    val tupleTypeArgs = tupleType.dealias.typeArgs
    if (caseClassUtil.fields.size == 0) {
      q"(t: ${tupleType.dealias}) => ${caseClassUtil.className}()"
    } else if (caseClassUtil.fields.size == 1) {
      verifySingleFieldCaseClass(c)(caseClassUtil, tupleType)
      // Compilation failure if `CaseClass` gets passed as `[Wrapper.CaseClass]` caused by invalid `className`
      // retrieval below, workaround available (see: https://github.com/softwaremill/tapir/issues/2540)
      q"(t: ${tupleType.dealias}) => ${caseClassUtil.className}(t)"
    } else {
      verifyCaseClassMatchesTuple(c)(caseClassUtil, tupleType, tupleTypeArgs)
      val ctorArgs = (1 to tupleTypeArgs.length).map(idx => q"t.${TermName(s"_$idx")}")
      // Compilation failure if `CaseClass` gets passed as `[Wrapper.CaseClass]` caused by invalid `className`
      // retrieval below, workaround available (see: https://github.com/softwaremill/tapir/issues/2540)
      q"(t: ${tupleType.dealias}) => ${caseClassUtil.className}(..$ctorArgs)"
    }
  }

  private def tupleFromCaseClass[TUPLE: c.WeakTypeTag, CASE_CLASS: c.WeakTypeTag](c: blackbox.Context): c.Tree = {
    import c.universe._

    val caseClassUtil = new CaseClassUtil[c.type, CASE_CLASS](c, "mapTo mapping")
    val tupleType = weakTypeOf[TUPLE]
    if (caseClassUtil.fields.size == 1) {
      verifySingleFieldCaseClass(c)(caseClassUtil, tupleType)
    } else {
      verifyCaseClassMatchesTuple(c)(caseClassUtil, tupleType, tupleType.dealias.typeArgs)
    }

    val tupleArgs = caseClassUtil.fields.map(field => q"t.${TermName(s"${field.name}")}")
    val classType = caseClassUtil.classSymbol.asType
    q"(t: $classType) => (..$tupleArgs)"
  }

  private def verifySingleFieldCaseClass[CASE_CLASS](
      c: blackbox.Context
  )(caseClassUtil: CaseClassUtil[c.type, CASE_CLASS], tupleType: c.Type): Unit = {
    val field = caseClassUtil.fields.head
    if (!(field.info.resultType =:= tupleType)) {
      c.abort(
        c.enclosingPosition,
        s"The type doesn't match the type of the case class field: $tupleType, $field"
      )
    }
  }

  private def verifyCaseClassMatchesTuple[CASE_CLASS](c: blackbox.Context)(
      caseClassUtil: CaseClassUtil[c.type, CASE_CLASS],
      tupleType: c.Type,
      tupleTypeArgs: List[c.Type]
  ): Unit = {
    val tupleSymbol = tupleType.typeSymbol
    if (!tupleSymbol.fullName.startsWith("scala.Tuple") && caseClassUtil.fields.nonEmpty) {
      c.abort(c.enclosingPosition, s"Expected source type to be a tuple, but got: ${tupleType.dealias}")
    }

    if (caseClassUtil.fields.size != tupleTypeArgs.size) {
      if (caseClassUtil.fields.size > 22) {
        c.abort(
          c.enclosingPosition,
          s"Cannot map to ${caseClassUtil.t}: arities of up to 22 are supported. If you need more inputs/outputs, map them to classes with less fields, and then combine these classes."
        )
      } else {
        c.abort(
          c.enclosingPosition,
          s"The arity of the source type (${tupleTypeArgs.size}) doesn't match the arity of the target type (${caseClassUtil.fields.size}): ${tupleType.dealias}, ${caseClassUtil.t}"
        )
      }
    }

    caseClassUtil.fields.zip(tupleTypeArgs).foreach { case (caseClassField, tupleArg) =>
      if (!(caseClassField.info.resultType =:= tupleArg)) {
        c.abort(
          c.enclosingPosition,
          s"The type of the tuple field doesn't match the type of the case class field ($caseClassField): $tupleArg, ${caseClassField.info.resultType}"
        )
      }
    }
  }
}
