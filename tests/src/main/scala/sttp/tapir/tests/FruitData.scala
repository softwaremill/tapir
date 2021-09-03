package sttp.tapir.tests

import sttp.model.Part
import sttp.tapir.internal.TapirFile

case class FruitData(data: Part[TapirFile])
