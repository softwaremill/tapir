package tapir
import tapir.Api.Info

case class Api(info: Info)

object Api {
  case class Info(
      title: String,
      version: String,
      description: Option[String] = None,
      termsOfService: Option[URL] = None,
      contact: Option[Contact] = None,
      license: Option[License] = None
  )
  case class Contact(name: Option[String], email: Option[Email], url: Option[URL])
  case class License(name: String, url: Option[URL])

  def apply(title: String, version: String): Api = {
    new Api(info = Info(title, version))
  }

  type Email = String
  type URL = String
}
