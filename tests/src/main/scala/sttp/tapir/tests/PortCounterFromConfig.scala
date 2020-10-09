package sttp.tapir.tests

import org.scalatest.{BeforeAndAfterAllConfigMap, ConfigMap, Suite}

trait PortCounterFromConfig extends BeforeAndAfterAllConfigMap { this: Suite =>
  override protected def beforeAll(configMap: ConfigMap): Unit = {
    super.beforeAll(configMap)
    val port = configMap.get("port").map(_.toString.toInt).getOrElse(50000)
    PortCounter.startWith(port)
  }
}
