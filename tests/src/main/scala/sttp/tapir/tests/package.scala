package sttp.tapir

import cats.effect.IO

package object tests {
  type Port = Int
  type KillSwitch = IO[Unit]
}
