package test

import shapeless.HNil
import typedapi.shared._

class Test {

  case class User(name: String) {}

  {
    import typedapi.dsl._

    val MyApi =
      // GET {body: User} /fetch/user?{name: String}
      (:= :> "fetch" :> "user" :> Query[String]('name) :> Get[Json, User]) :|:
        // POST {body: User} /create/user
        (:= :> "create" :> "user" :> ReqBody[Json, User] :> Post[Json, User])
  }

//  {
//    import typedapi._
//    import typedapi.dsl.Query
//    val api2 =
//      api(method = Get[Json, User], path = Root / "fetch" / "user", queries = Queries add Query[String]('name))
//  }

}
