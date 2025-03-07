package sttp.tapir.client.sttp4

import sttp.tapir.client.sttp4.basic.BasicSttpClientTestsSender
import sttp.tapir.client.tests.ClientMultipartTests

class SttpClientMultipartTests extends BasicSttpClientTestsSender with ClientMultipartTests {
  multipartTests()
}
