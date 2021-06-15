package sttp.tapir.docs.openapi.dtos

final case class Items[A](items: List[A], total: Int)
