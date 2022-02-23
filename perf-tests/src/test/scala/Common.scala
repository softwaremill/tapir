package perfTests

import io.gatling.core.Predef._
import io.gatling.http.Predef._

object Common {
  val scn = scenario("get jsons")
    .during(5*60) {
      exec(http("first json")
             .get("/4"))
    }
}
