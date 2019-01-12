import tapir.generic.Configuration

package object tapir extends Tapir {
  type StatusCode = Int
  implicit val defaultGenericConfiguration: Configuration = Configuration.default
}
