package sttp.tapir.docs.openapi.dtos

case class Country(name: String)
case class Author(name: String, country: Country)
case class Genre(name: String, description: String)
case class Book(title: String, genre: Genre, year: Int, author: Author)
case class BooksQuery(genre: Option[String], limit: Option[Int])
