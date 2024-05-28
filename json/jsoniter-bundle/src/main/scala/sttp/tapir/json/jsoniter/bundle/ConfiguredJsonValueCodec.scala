package sttp.tapir.json.jsoniter.bundle

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.{CodecMakerConfig, JsonCodecMaker}

trait ConfiguredJsonValueCodec[A] extends JsonValueCodec[A]

// see https://github.com/plokhotnyuk/jsoniter-scala/issues/852#issuecomment-2017582987
object ConfiguredJsonValueCodec:
  inline def derived[A](using inline config: CodecMakerConfig = CodecMakerConfig): ConfiguredJsonValueCodec[A] = new:
    private val impl = JsonCodecMaker.make[A](config)
    export impl._
