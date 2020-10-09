package sttp.tapir.tests

import org.scalatest.{BeforeAndAfterAllConfigMap, ConfigMap, Suite}

trait PortCounterFromConfig extends BeforeAndAfterAllConfigMap { this: Suite =>
  override protected def beforeAll(configMap: ConfigMap): Unit = {
    super.beforeAll(configMap)
    configMap.get("port").map(_.toString.toInt).foreach { port =>
      PortCounter.startWith(port)
    }
  }
}
