package sttp.tapir.codec.iron

import sttp.tapir.Schema
import io.github.iltotore.iron.Constraint
import io.github.iltotore.iron.:|
import io.github.iltotore.iron.refineEither
import io.github.iltotore.iron.refineOption
import sttp.tapir.CodecFormat
import sttp.tapir.Codec
import sttp.tapir.DecodeResult
import sttp.tapir.Validator
import sttp.tapir.ValidationResult
import scala.reflect.ClassTag
import sttp.tapir.ValidationError

trait TapirCodecIron {

  inline given [Value, Predicate: ClassTag](using
      inline vSchema: Schema[Value],
      inline constraint: Constraint[Value, Predicate]
  ): Schema[Value :| Predicate] =
    vSchema.validate(validator).map[Value :| Predicate](v => v.refineOption[Predicate])(identity)

  inline given [R, V, P: ClassTag, CF <: CodecFormat](using
      inline tm: Codec[R, V, CF],
      inline constraint: Constraint[V, P]
  ): Codec[R, V :| P, CF] = {

    implicitly[Codec[R, V, CF]]
      .validate(validator)
      .mapDecode { (v: V) =>
        v.refineEither[P] match {
          case Right(refined) => DecodeResult.Value[V :| P](refined)
          case Left(errorMessage) =>
            DecodeResult.InvalidValue(
              List(ValidationError[V](validator, v, Nil, Some(errorMessage)))
            )
        }
      }(identity)
  }

  // TODO currently only uses custom validator, should be changed to other variants based on selected constraint
  private inline def validator[V, P: ClassTag](using inline constraint: Constraint[V, P]): Validator.Custom[V] = Validator.Custom { v =>
    if (constraint.test(v)) ValidationResult.Valid
    else ValidationResult.Invalid(implicitly[ClassTag[P]].runtimeClass.toString)
  }
}
