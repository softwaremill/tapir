package sttp.tapir.serverless.aws.cdk.internal

sealed trait Method

object Method {
  case object GET extends Method
  case object POST extends Method
  case object PATCH extends Method
  case object PUT extends Method
  case object DELETE extends Method
  case object HEAD extends Method
  case object OPTIONS extends Method
  case object CONNECT extends Method
  case object TRACE extends Method

  implicit val methodOrdering: Ordering[Method] = Ordering.by[Method, Int] {
    case GET     => 0
    case POST    => 1
    case PUT     => 2
    case PATCH   => 3
    case DELETE  => 4
    case HEAD    => 5
    case OPTIONS => 6
    case CONNECT => 7
    case TRACE   => 8
  }

  def apply(method: String): Option[Method] = method match {
    case "GET"     => Some(GET)
    case "POST"    => Some(POST)
    case "DELETE"  => Some(DELETE)
    case "PATCH"   => Some(PATCH)
    case "PUT"     => Some(PUT)
    case "HEAD"    => Some(HEAD)
    case "OPTIONS" => Some(OPTIONS)
    case "CONNECT" => Some(CONNECT)
    case "TRACE"   => Some(TRACE)
    case _         => None
  }
}
