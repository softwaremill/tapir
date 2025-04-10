package sttp.tapir.swagger

import org.scalatest.funsuite.AsyncFunSuite

class SwaggerUITest extends AsyncFunSuite {

  private val swaggerInitializerJs = """
      |window.onload = function() {
      |  //<editor-fold desc="Changeable Configuration Block">
      |
      |  // the following lines will be replaced by docker/configurator, when it runs in a docker-container
      |  window.ui = SwaggerUIBundle({
      |    url: "./docs.yaml",
      |    dom_id: '#swagger-ui',
      |    deepLinking: true,
      |    presets: [
      |      SwaggerUIBundle.presets.apis,
      |      SwaggerUIStandalonePreset
      |    ],
      |    plugins: [
      |      SwaggerUIBundle.plugins.DownloadUrl
      |    ],
      |    layout: "StandaloneLayout"
      |  });
      |
      |  //</editor-fold>
      |};
      |""".stripMargin

  // We trim strings before comparing, since this can cause issues.
  def customTrim(a: String): String = {
    a.replaceAll("\r", "").replaceAll("\n", "").replaceAll(" ", "")
  }

  private def oAuthStringWrap(a: String) = "window.ui.initOAuth({" + a + "});"

  private val (key1, value1, key2, value2, key3, value3) = ("Apple", "\"Tart\"", "Banana", "Bread", "Coconut", "Tree")
  private val injection1 = s"$key1: $value1,"
  private val injection2 = injection1 + s"$key2: $value2,"
  private val injection3 = injection2 + s"$key3: $value3,"
  private val initOptions1 = List((key1, value1))
  private val initOptions2 = initOptions1 :+ (key2, value2)
  private val initOptions3 = initOptions2 :+ (key3, value3)

  private val (oKey1, oValue1, oKey2, oValue2, oKey3, oValue3) = ("Ice", "Coffee", "Mexican", "\"Burrito\"", "\"French\"", "Fries")
  private val oInjection1 = s"$oKey1: $oValue1,"
  private val oInjection2 = oInjection1 + s"$oKey2: $oValue2,"
  private val oInjection3 = oInjection2 + s"$oKey3: $oValue3,"
  private val oAuthInitOptions1 = List((oKey1, oValue1))
  private val oAuthInitOptions2 = oAuthInitOptions1 :+ (oKey2, oValue2)
  private val oAuthInitOptions3 = oAuthInitOptions2 :+ (oKey3, oValue3)
  private val initOptionsList =
    List((None, ""), (Some(initOptions1.toMap), injection1), (Some(initOptions2.toMap), injection2), (Some(initOptions3.toMap), injection3))
  private val oAuthInitOptionsList = List(
    (None, ""),
    (Some(Map.empty[String, String]), oAuthStringWrap("")),
    (Some(oAuthInitOptions1.toMap), oAuthStringWrap(oInjection1)),
    (Some(oAuthInitOptions2.toMap), oAuthStringWrap(oInjection2)),
    (Some(oAuthInitOptions3.toMap), oAuthStringWrap(oInjection3))
  )

  test("SwaggerUI optionsInjection for String, Int and Boolean") {
    val (key1, value1) = ("Apple", "\"Tart\"")
    val (key2, value2) = ("Banana", "2")
    val (key3, value3) = ("Coconut", "true")
    val injection1 = s"$key1: $value1,"
    val injection2 = injection1 + s"$key2: $value2,"
    val injection3 = injection2 + s"$key3: $value3,"
    val initOptions1 = List((key1, value1))
    val initOptions2 = initOptions1 :+ (key2, value2)
    val initOptions3 = initOptions2 :+ (key3, value3)
    for ((injection, initOptions) <- Seq((injection1, initOptions1), (injection2, initOptions2), (injection3, initOptions3))) {
      val options = SwaggerUIOptions.default.copy(initializerOptions = Some(initOptions.toMap[String, String]))
      val result = SwaggerUI.optionsInjection(swaggerInitializerJs = swaggerInitializerJs, options = options)
      val expectedResult =
        s"""
           |window.onload = function() {
           |  //<editor-fold desc="Changeable Configuration Block">
           |
           |  // the following lines will be replaced by docker/configurator, when it runs in a docker-container
           |  window.ui = SwaggerUIBundle({
           |    $injection
           |    showExtensions: false,
           |    url: "./docs.yaml",
           |    dom_id: '#swagger-ui',
           |    deepLinking: true,
           |    presets: [
           |      SwaggerUIBundle.presets.apis,
           |      SwaggerUIStandalonePreset
           |    ],
           |    plugins: [
           |      SwaggerUIBundle.plugins.DownloadUrl
           |    ],
           |    layout: "StandaloneLayout"
           |  });
           |
           |  //</editor-fold>
           |};
           |""".stripMargin
      assert(customTrim(result) == customTrim(expectedResult))
    }
    assert(true)
  }

  test("SwaggerUI optionsInjection Combination Testing") {
    initOptionsList.foreach({ case (initOptions, injection) =>
      oAuthInitOptionsList.foreach({ case (oAuthInitOptions, oInjection) =>
        val options = SwaggerUIOptions.default.copy(initializerOptions = initOptions, oAuthInitOptions = oAuthInitOptions)
        val result = SwaggerUI.optionsInjection(swaggerInitializerJs, options)
        val expected = s"""
                           |window.onload = function() {
                           |  //<editor-fold desc="Changeable Configuration Block">
                           |
                           |  // the following lines will be replaced by docker/configurator, when it runs in a docker-container
                           |  window.ui = SwaggerUIBundle({
                           |    $injection
                           |    showExtensions: false,
                           |    url: "./docs.yaml",
                           |    dom_id: '#swagger-ui',
                           |    deepLinking: true,
                           |    presets: [
                           |      SwaggerUIBundle.presets.apis,
                           |      SwaggerUIStandalonePreset
                           |    ],
                           |    plugins: [
                           |      SwaggerUIBundle.plugins.DownloadUrl
                           |    ],
                           |    layout: "StandaloneLayout"
                           |  });
                           |  $oInjection
                           |
                           |  //</editor-fold>
                           |};
                           |""".stripMargin
        assert(customTrim(result) == customTrim(expected))
      })
    })
    assert(true)
  }
}
