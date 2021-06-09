package sttp.tapir

case class ShadowedEndpoint(e: Endpoint[_, _, _, _], by: Endpoint[_, _, _, _])
