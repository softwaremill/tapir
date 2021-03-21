package sttp.tapir.server

import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.stream.StreamMessage

package object armeria {
  type ArmeriaResponseType = Either[StreamMessage[HttpData], HttpData]
}
