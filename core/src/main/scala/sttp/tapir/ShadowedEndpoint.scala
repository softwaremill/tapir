package sttp.tapir

case class ShadowedEndpoint[I, E, O, R](e: Endpoint[I, E, O, R], by: Endpoint[I, E, O, R]) {

}
