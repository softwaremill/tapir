package sttp.tapir.server.netty.internal

sealed abstract class Choice3[+A, +B, +C]

final case class Left[+A, +B, +C](value: A) extends Choice3[A, B, C]
final case class Middle[+A, +B, +C](value: B) extends Choice3[A, B, C]
final case class Right[+A, +B, +C](value: C) extends Choice3[A, B, C]
