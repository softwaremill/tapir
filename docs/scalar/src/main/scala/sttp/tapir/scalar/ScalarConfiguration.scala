package sttp.tapir.scalar

/** Configuration options for the Scalar API reference UI.
  * See https://scalar.com/products/api-references/configuration for full documentation.
  *
  * Complex object/array options (`authentication`, `defaultHttpClient`, `hiddenClients`, `metaData`, `mcp`, `pathRouting`, `servers`) accept
  * raw JSON strings that are embedded directly into the JavaScript configuration object.
  *
  * @param authentication
  *   Prefill credentials for users. Raw JSON string of `AuthenticationConfiguration`.
  * @param baseServerURL
  *   Prefix all relative servers with a base URL.
  * @param customCss
  *   Inject custom CSS styling into the component.
  * @param darkMode
  *   Initial dark mode state. Defaults to `false`.
  * @param defaultHttpClient
  *   Set the default HTTP client for code examples. Raw JSON string of `{ targetKey, clientKey }`.
  * @param defaultOpenAllTags
  *   Start with all tags expanded. Defaults to `false`.
  * @param defaultOpenFirstTag
  *   Whether to open the first tag if the URL doesn't contain a specific target. Defaults to `true`.
  * @param documentDownloadType
  *   Sets the file type of the document to download. One of `'json'`, `'yaml'`, `'both'`, `'direct'`, `'none'`.
  * @param expandAllModelSections
  *   Open all model sections by default. Defaults to `false`.
  * @param expandAllResponses
  *   Open response sections by default. Defaults to `false`.
  * @param favicon
  *   Path to favicon for the documentation page.
  * @param forceDarkModeState
  *   Force dark mode to always be a specific state. One of `'dark'`, `'light'`.
  * @param hideClientButton
  *   Hide the client button from sidebar and modal. Defaults to `false`.
  * @param hideDarkModeToggle
  *   Hide the dark mode toggle. Defaults to `false`.
  * @param hiddenClients
  *   Control which HTTP clients are displayed. Raw JSON string: `[]`, `true`, or an object mapping languages to clients.
  * @param hideModels
  *   Hide models from sidebar and content. Defaults to `false`.
  * @param hideSearch
  *   Hide the sidebar search bar. Defaults to `false`.
  * @param hideTestRequestButton
  *   Hide "Test Request" button and auth panel. Defaults to `false`.
  * @param isLoading
  *   Controls whether the references show a loading state. Defaults to `false`.
  * @param layout
  *   Layout style for the API reference. One of `'modern'` (default), `'classic'`.
  * @param metaData
  *   Configure page meta information. Raw JSON string with title, description, og tags, Twitter card fields.
  * @param mcp
  *   MCP (Model Context Protocol) configuration. Raw JSON string of `{ name, url, disabled? }`.
  * @param oauth2RedirectUri
  *   Default OAuth 2.0 redirect URI.
  * @param operationTitleSource
  *   Source for display text and search for operations. One of `'summary'` (default), `'path'`.
  * @param orderRequiredPropertiesFirst
  *   Order required properties first in schema objects. Defaults to `true`.
  * @param orderSchemaPropertiesBy
  *   Control schema property ordering. One of `'alpha'` (default), `'preserve'`.
  * @param pathRouting
  *   Configuration for path-based routing. Raw JSON string of `{ basePath: string }`.
  * @param persistAuth
  *   Persist authentication credentials in local storage. Defaults to `false`.
  * @param proxyUrl
  *   Proxy URL for cross-origin requests.
  * @param searchHotKey
  *   Key used with CTRL/CMD to open search. Defaults to `'k'`.
  * @param servers
  *   Override the OpenAPI servers. Raw JSON string of `Server[]`.
  * @param showDeveloperTools
  *   When to show developer tools. One of `'always'`, `'localhost'` (default), `'never'`.
  * @param showOperationId
  *   Display operation ID in the UI. Defaults to `false`.
  * @param showSidebar
  *   Display the sidebar. Defaults to `true`.
  * @param telemetry
  *   Enable telemetry when analytics plugin is loaded. Defaults to `true`.
  * @param theme
  *   Color scheme / visual theme. One of `'default'`, `'alternate'`, `'moon'`, `'purple'`, `'solarized'`, `'bluePlanet'`, `'saturn'`,
  *   `'kepler'`, `'mars'`, `'deepSpace'`, `'laserwave'`, `'none'`.
  * @param withDefaultFonts
  *   Load default fonts from CDN. Defaults to `true`.
  */
case class ScalarConfiguration(
    authentication: Option[String] = None,
    baseServerURL: Option[String] = None,
    customCss: Option[String] = None,
    darkMode: Option[Boolean] = None,
    defaultHttpClient: Option[String] = None,
    defaultOpenAllTags: Option[Boolean] = None,
    defaultOpenFirstTag: Option[Boolean] = None,
    documentDownloadType: Option[String] = None,
    expandAllModelSections: Option[Boolean] = None,
    expandAllResponses: Option[Boolean] = None,
    favicon: Option[String] = None,
    forceDarkModeState: Option[String] = None,
    hideClientButton: Option[Boolean] = None,
    hideDarkModeToggle: Option[Boolean] = None,
    hiddenClients: Option[String] = None,
    hideModels: Option[Boolean] = None,
    hideSearch: Option[Boolean] = None,
    hideTestRequestButton: Option[Boolean] = None,
    isLoading: Option[Boolean] = None,
    layout: Option[String] = None,
    metaData: Option[String] = None,
    mcp: Option[String] = None,
    oauth2RedirectUri: Option[String] = None,
    operationTitleSource: Option[String] = None,
    orderRequiredPropertiesFirst: Option[Boolean] = None,
    orderSchemaPropertiesBy: Option[String] = None,
    pathRouting: Option[String] = None,
    persistAuth: Option[Boolean] = None,
    proxyUrl: Option[String] = None,
    searchHotKey: Option[String] = None,
    servers: Option[String] = None,
    showDeveloperTools: Option[String] = None,
    showOperationId: Option[Boolean] = None,
    showSidebar: Option[Boolean] = None,
    telemetry: Option[Boolean] = None,
    theme: Option[String] = None,
    withDefaultFonts: Option[Boolean] = None
) {
  def authentication(v: String): ScalarConfiguration = copy(authentication = Some(v))
  def baseServerURL(v: String): ScalarConfiguration = copy(baseServerURL = Some(v))
  def customCss(v: String): ScalarConfiguration = copy(customCss = Some(v))
  def darkMode(v: Boolean): ScalarConfiguration = copy(darkMode = Some(v))
  def defaultHttpClient(v: String): ScalarConfiguration = copy(defaultHttpClient = Some(v))
  def defaultOpenAllTags(v: Boolean): ScalarConfiguration = copy(defaultOpenAllTags = Some(v))
  def defaultOpenFirstTag(v: Boolean): ScalarConfiguration = copy(defaultOpenFirstTag = Some(v))
  def documentDownloadType(v: String): ScalarConfiguration = copy(documentDownloadType = Some(v))
  def expandAllModelSections(v: Boolean): ScalarConfiguration = copy(expandAllModelSections = Some(v))
  def expandAllResponses(v: Boolean): ScalarConfiguration = copy(expandAllResponses = Some(v))
  def favicon(v: String): ScalarConfiguration = copy(favicon = Some(v))
  def forceDarkModeState(v: String): ScalarConfiguration = copy(forceDarkModeState = Some(v))
  def hideClientButton(v: Boolean): ScalarConfiguration = copy(hideClientButton = Some(v))
  def hideDarkModeToggle(v: Boolean): ScalarConfiguration = copy(hideDarkModeToggle = Some(v))
  def hiddenClients(v: String): ScalarConfiguration = copy(hiddenClients = Some(v))
  def hideModels(v: Boolean): ScalarConfiguration = copy(hideModels = Some(v))
  def hideSearch(v: Boolean): ScalarConfiguration = copy(hideSearch = Some(v))
  def hideTestRequestButton(v: Boolean): ScalarConfiguration = copy(hideTestRequestButton = Some(v))
  def isLoading(v: Boolean): ScalarConfiguration = copy(isLoading = Some(v))
  def layout(v: String): ScalarConfiguration = copy(layout = Some(v))
  def metaData(v: String): ScalarConfiguration = copy(metaData = Some(v))
  def mcp(v: String): ScalarConfiguration = copy(mcp = Some(v))
  def oauth2RedirectUri(v: String): ScalarConfiguration = copy(oauth2RedirectUri = Some(v))
  def operationTitleSource(v: String): ScalarConfiguration = copy(operationTitleSource = Some(v))
  def orderRequiredPropertiesFirst(v: Boolean): ScalarConfiguration = copy(orderRequiredPropertiesFirst = Some(v))
  def orderSchemaPropertiesBy(v: String): ScalarConfiguration = copy(orderSchemaPropertiesBy = Some(v))
  def pathRouting(v: String): ScalarConfiguration = copy(pathRouting = Some(v))
  def persistAuth(v: Boolean): ScalarConfiguration = copy(persistAuth = Some(v))
  def proxyUrl(v: String): ScalarConfiguration = copy(proxyUrl = Some(v))
  def searchHotKey(v: String): ScalarConfiguration = copy(searchHotKey = Some(v))
  def servers(v: String): ScalarConfiguration = copy(servers = Some(v))
  def showDeveloperTools(v: String): ScalarConfiguration = copy(showDeveloperTools = Some(v))
  def showOperationId(v: Boolean): ScalarConfiguration = copy(showOperationId = Some(v))
  def showSidebar(v: Boolean): ScalarConfiguration = copy(showSidebar = Some(v))
  def telemetry(v: Boolean): ScalarConfiguration = copy(telemetry = Some(v))
  def theme(v: String): ScalarConfiguration = copy(theme = Some(v))
  def withDefaultFonts(v: Boolean): ScalarConfiguration = copy(withDefaultFonts = Some(v))
}

object ScalarConfiguration {
  val default: ScalarConfiguration = ScalarConfiguration()
}
