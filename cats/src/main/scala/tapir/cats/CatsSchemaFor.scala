package tapir
package cats

import _root_.cats.data.{NonEmptyChain, NonEmptyList, NonEmptySet}

trait CatsSchemaFor {
  implicit def schemaForNec[T: SchemaFor]: SchemaFor[NonEmptyChain[T]] = new SchemaFor[NonEmptyChain[T]] {
    def schema: Schema = Schema.SArray(implicitly[SchemaFor[T]].schema)
  }

  implicit def schemaForNes[T: SchemaFor]: SchemaFor[NonEmptySet[T]] = new SchemaFor[NonEmptySet[T]] {
    def schema: Schema = Schema.SArray(implicitly[SchemaFor[T]].schema)
  }

  implicit def schemaForNel[T: SchemaFor]: SchemaFor[NonEmptyList[T]] = new SchemaFor[NonEmptyList[T]] {
    def schema: Schema = Schema.SArray(implicitly[SchemaFor[T]].schema)
  }
}
