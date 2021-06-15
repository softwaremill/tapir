package sttp.tapir

/** Endpoint e1 is shadowed by endpoint e2 when all requests that match e2 also match e1 (in this context "request matches
  * endpoint" means that request and endpoint have the same method and path). If e2 is shadowed by e1 it means that e2 will
  * be never called because all requests will be handled by e1 beforehand. Examples where e2 is shadowed by e1:
  *
  * e1 = endpoint.get.in("x" / paths)
  * e2 = endpoint.get.in("x" / "y" / "x")
  *
  * e1 = endpoint.get.in(path[String].name("y_1") / path[String].name("y_2"))
  * e2 = endpoint.get.in(path[String].name("y_3") / path[String].name("y_4"))
  */

case class ShadowedEndpoint(e: Endpoint[_, _, _, _], by: Endpoint[_, _, _, _]) {
  override def toString = e.input.show + ", is shadowed by: " + by.input.show
}