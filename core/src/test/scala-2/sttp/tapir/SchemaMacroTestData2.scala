package sttp.tapir

object SchemaMacroTestData2 {
  object ValueClasses {
    case class UserName(name: String) extends AnyVal
    case class DoubleValue(value: Double) extends AnyVal
    case class UserNameRequest(name: UserName)

    case class UserList(list: List[UserName]) extends AnyVal
    case class UserListRequest(list: UserList)
  }

  sealed trait Type
  object Type {
    final case class Num[N <: AnyVal: Numeric](n: N) extends Type
    final case class MapType(obj: Map[String, Type]) extends Type
  }
}
