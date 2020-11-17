package sttp.tapir.tests

import sttp.model.Part
import sttp.tapir.TapirFile

case class FruitData(data: Part[TapirFile])
