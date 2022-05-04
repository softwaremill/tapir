package sttp.tapir.generic

import java.io.File

trait MultipartCodecDerivationTestExtensions {
  def createTempFile() = File.createTempFile("tapir", "test")
}