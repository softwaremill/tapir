import shapeless.test.illTyped

/** Negative compile-time checks: these assignments must not typecheck when aliasing is correct. */
object CompileAssertions {

  def assertAll(): Unit = {
    // --- Pet redefines ---
    illTyped("""
      val pet: sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet =
        sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet("Rex", Some("dog"))
      val x: sttp.tapir.generated.redefine.pet_extra.TapirGeneratedEndpoints.Pet = pet
    """)
    illTyped("""
      val pet: sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet =
        sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet("Rex", Some("dog"))
      val x: sttp.tapir.generated.redefine.pet_required.TapirGeneratedEndpoints.Pet = pet
    """)
    illTyped("""
      val pet: sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet =
        sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet("Rex", Some("dog"))
      val x: sttp.tapir.generated.redefine.pet_type.TapirGeneratedEndpoints.Pet = pet
    """)
    illTyped("""
      val pet: sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet =
        sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet("Rex", Some("dog"))
      val x: sttp.tapir.generated.redefine.pet_nullable.TapirGeneratedEndpoints.Pet = pet
    """)
    illTyped("""
      val pet: sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet =
        sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet("Rex", Some("dog"))
      val x: sttp.tapir.generated.redefine.allof_pet.TapirGeneratedEndpoints.Pet = pet
    """)

    // --- enum / nested / oneOf redefines ---
    illTyped("""
      val status: sttp.tapir.generated.core.TapirGeneratedEndpoints.Status =
        sttp.tapir.generated.core.TapirGeneratedEndpoints.Status.ACTIVE
      val x: sttp.tapir.generated.redefine.enum.TapirGeneratedEndpoints.Status = status
    """)
    illTyped("""
      val person: sttp.tapir.generated.core.TapirGeneratedEndpoints.Person =
        sttp.tapir.generated.core.TapirGeneratedEndpoints.Person(
          "Alice",
          Some(sttp.tapir.generated.core.TapirGeneratedEndpoints.Address("London", None))
        )
      val x: sttp.tapir.generated.redefine.nested.TapirGeneratedEndpoints.Person = person
    """)
    illTyped("""
      val address: sttp.tapir.generated.core.TapirGeneratedEndpoints.Address =
        sttp.tapir.generated.core.TapirGeneratedEndpoints.Address("London", None)
      val x: sttp.tapir.generated.redefine.nested.TapirGeneratedEndpoints.Address = address
    """)
    illTyped("""
      val animal: sttp.tapir.generated.core.TapirGeneratedEndpoints.Animal =
        sttp.tapir.generated.core.TapirGeneratedEndpoints.Dog(barks = true)
      val x: sttp.tapir.generated.redefine.oneof.TapirGeneratedEndpoints.Animal = animal
    """)

    // --- map / array / alias / default / widget redefines ---
    illTyped("""
      val tags: sttp.tapir.generated.core.TapirGeneratedEndpoints.TagMap = Map("a" -> "b")
      val x: sttp.tapir.generated.redefine.map.TapirGeneratedEndpoints.TagMap = tags
    """)
    illTyped("""
      val ids: sttp.tapir.generated.core.TapirGeneratedEndpoints.IdList = List(1L, 2L)
      val x: sttp.tapir.generated.redefine.array.TapirGeneratedEndpoints.IdList = ids
    """)
    illTyped("""
      val name: sttp.tapir.generated.core.TapirGeneratedEndpoints.PetName = "fluffy"
      val x: sttp.tapir.generated.redefine.alias.TapirGeneratedEndpoints.PetName = name
    """)
    illTyped("""
      val order: sttp.tapir.generated.core.TapirGeneratedEndpoints.Order =
        sttp.tapir.generated.core.TapirGeneratedEndpoints.Order(
          1L,
          sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet("Rex", Some("dog"))
        )
      val x: sttp.tapir.generated.redefine.default.TapirGeneratedEndpoints.Order = order
    """)
    illTyped("""
      val widget: sttp.tapir.generated.core.TapirGeneratedEndpoints.Widget =
        sttp.tapir.generated.core.TapirGeneratedEndpoints.Widget("w1", Some("label"))
      val x: sttp.tapir.generated.redefine.shared_widget.TapirGeneratedEndpoints.Widget = widget
    """)

    // --- mixed / transitive ---
    illTyped("""
      val order: sttp.tapir.generated.core.TapirGeneratedEndpoints.Order =
        sttp.tapir.generated.core.TapirGeneratedEndpoints.Order(
          1L,
          sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet("Rex", Some("dog"))
        )
      val x: sttp.tapir.generated.partial.order.TapirGeneratedEndpoints.Order = order
    """)
    illTyped("""
      val pet: sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet =
        sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet("Rex", Some("dog"))
      val x: sttp.tapir.generated.partial.order.TapirGeneratedEndpoints.Pet = pet
    """)
    illTyped("""
      val order: sttp.tapir.generated.core.TapirGeneratedEndpoints.Order =
        sttp.tapir.generated.core.TapirGeneratedEndpoints.Order(
          1L,
          sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet("Rex", Some("dog"))
        )
      val x: sttp.tapir.generated.transitive.mismatch.TapirGeneratedEndpoints.Order = order
    """)
    illTyped("""
      val pet: sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet =
        sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet("Rex", Some("dog"))
      val x: sttp.tapir.generated.transitive.mismatch.TapirGeneratedEndpoints.Pet = pet
    """)

    // --- chain ---
    illTyped("""
      val pet: sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet =
        sttp.tapir.generated.core.TapirGeneratedEndpoints.Pet("Rex", Some("dog"))
      val x: sttp.tapir.generated.chain.leaf.TapirGeneratedEndpoints.LegacyPet = pet
    """)
  }
}
