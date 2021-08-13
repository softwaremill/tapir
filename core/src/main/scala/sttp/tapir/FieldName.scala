package sttp.tapir

case class FieldName(name: String, encodedName: String)
object FieldName {
  def apply(name: String) = new FieldName(name, name)
}

case class FieldNames(fieldPath: List[FieldName]) {

  def prependPath(f: FieldName): FieldNames =
    copy(fieldPath = f :: fieldPath)

  /** e.g. `user.address.street.name`.
    */
  val asPath: Option[FieldPath] =
    fieldPath match {
      case Nil => None
      case l   => Some(FieldPath(l.map(_.encodedName).mkString(".")))
    }
}
object FieldNames {
  val empty: FieldNames = FieldNames(Nil)
}

case class FieldPath(value: String) extends AnyVal {
  override def toString: String = value
}
