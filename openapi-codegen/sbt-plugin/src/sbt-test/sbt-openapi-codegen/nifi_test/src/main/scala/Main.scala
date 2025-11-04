import sttp.tapir.generated.TapirGeneratedEndpointsSchemas1._

object Main extends App {

  val s = controllerServiceReferencingComponentDTOTapirSchema
  val t = controllerServiceReferencingComponentEntityTapirSchema
}
