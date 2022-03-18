package perfTests

import sttp.tapir._
import scala.io.StdIn

object Common {
  def genTapirEndpoint(n: Int): PublicEndpoint[Int, String, String, Any] = endpoint.get
    .in("path" + n.toString)
    .in(path[Int]("id"))
    .errorOut(stringBody)
    .out(stringBody)

  def blockServer(): Unit = {
    println(Console.BLUE + "Server now online. Please navigate to http://localhost:8080/path0/1\nPress RETURN to stop..." + Console.RESET)
    StdIn.readLine()
    println("Server terminated")
  }
}
