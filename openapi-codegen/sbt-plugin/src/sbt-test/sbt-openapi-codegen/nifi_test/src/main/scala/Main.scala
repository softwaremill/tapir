import sttp.tapir.generated.TapirGeneratedEndpointsSchemas._

object Main extends App {

  val s = controllerServiceReferencingComponentDTOTapirSchema
  val t = controllerServiceReferencingComponentEntityTapirSchema
}
