import sttp.tapir.Schema
import sttp.tapir.generated.TapirGeneratedEndpointsSchemas16.controllerServiceReferencingComponentDTOTapirSchema

case class O(component: Option[N] = None)
case class N(referencingComponents: Option[Set[O]] = None)
object Main extends App {

  import sttp.tapir.generic.auto._
  // WORKS:
//  val (s1, s2) = {
//    implicit lazy val schema1: sttp.tapir.Schema[N] = sttp.tapir.Schema.derived
//    val schema2: sttp.tapir.Schema[O] = sttp.tapir.Schema.derived
//    (schema1, schema2)
//  }
//  implicit val schema1: sttp.tapir.Schema[N] = s1
//  implicit val schema2: sttp.tapir.Schema[O] = s2
  // Does it?
  implicit lazy val schema1: sttp.tapir.Schema[N] = {
    val schema2 = null // shadow mutually-recursive schema
    sttp.tapir.Schema.derived
  }
  implicit lazy val schema2: sttp.tapir.Schema[O] = {
    val schema1 = null // shadow mutually-recursive schema
    sttp.tapir.Schema.derived
  }
  val s = schema1.applyValidation(N(Some(Set(O(Some(N(None)))))))
  val t = schema2.applyValidation(O(Some(N(Some(Set(O(Some(N(None)))))))))
  //  val schema3 = controllerServiceReferencingComponentDTOTapirSchema
}
