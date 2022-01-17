package sttp.tapir

object SchemaMacroTestData2 {
  object ValueClasses {
    case class UserName(name: String) extends AnyVal
    case class UserNameRequest(name: UserName)

    case class UserList(list: List[UserName]) extends AnyVal
    case class UserListRequest(list: UserList)
  }
}
