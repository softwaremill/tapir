package sttp.tapir

case class ShadowedEndpoint(e: Endpoint[_, _, _, _], by: Endpoint[_, _, _, _]) {
  override def toString = e.input.show + ", is shadowed by: " + by.input.show
}
