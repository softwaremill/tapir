package sttp.tapir.json.pickler

import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType.*
import sttp.tapir.{Schema, Validator}

/** Checks that a JSON document has the shape a tapir `Schema` advertises: field names, optionality, discriminator field and values,
  * enumeration values, primitive kinds. Returns every mismatch found, with a JSON path.
  *
  * This is the property the module exists for -- the codec and the schema come from one expansion, so the JSON the former writes must
  * always be the JSON the latter documents -- and the one thing no per-fixture string assertion can establish in general.
  */
object SchemaJsonAgreement {

  def mismatches(schema: Schema[?], json: ujson.Value): List[String] =
    check(schema, json, "$", collectNamed(schema, Map.empty))

  /** Every named schema reachable from `schema`, so that an `SRef` can be followed. */
  private def collectNamed(schema: Schema[?], acc: Map[SName, Schema[?]]): Map[SName, Schema[?]] = {
    val withSelf = schema.name match {
      case Some(name) if acc.contains(name) => return acc
      case Some(name)                       => acc + (name -> schema)
      case None                             => acc
    }
    schema.schemaType match {
      case p: SProduct[?]        => p.fields.foldLeft(withSelf)((m, f) => collectNamed(f.schema, m))
      case c: SCoproduct[?]      => c.subtypes.foldLeft(withSelf)((m, s) => collectNamed(s, m))
      case o: SOption[?, ?]      => collectNamed(o.element, withSelf)
      case a: SArray[?, ?]       => collectNamed(a.element, withSelf)
      case o: SOpenProduct[?, ?] => o.fields.foldLeft(collectNamed(o.valueSchema, withSelf))((m, f) => collectNamed(f.schema, m))
      case _                     => withSelf
    }
  }

  private def check(s: Schema[?], j: ujson.Value, path: String, named: Map[SName, Schema[?]]): List[String] =
    s.schemaType match {
      case SRef(name) =>
        named.get(name) match {
          case Some(target) => check(target, j, path, named)
          case None         => List(s"$path: unresolved SRef($name)")
        }
      case _ if j == ujson.Null =>
        if (s.isOptional) Nil else List(s"$path: null, but the schema is not optional")
      case o: SOption[?, ?] => check(o.element, j, path, named)
      case p: SProduct[?]   =>
        j match {
          case obj: ujson.Obj =>
            val fields = p.fields.map(f => f.name.encodedName -> f).toMap
            val unknown = obj.value.keys.toList.filterNot(fields.contains).map(k => s"$path.$k: written but not in the schema")
            val missing = fields.toList.collect {
              case (n, f) if !f.schema.isOptional && !obj.value.contains(n) => s"$path.$n: required by the schema but absent"
            }
            val nested = obj.value.toList.flatMap { case (k, v) =>
              fields.get(k).toList.flatMap(f => check(f.schema, v, s"$path.$k", named))
            }
            unknown ++ missing ++ nested
          case other => List(s"$path: expected an object for SProduct, got ${kind(other)}")
        }
      case c: SCoproduct[?] =>
        c.discriminator match {
          case Some(d) =>
            j match {
              case obj: ujson.Obj =>
                obj.value.get(d.name.encodedName) match {
                  case Some(ujson.Str(value)) =>
                    d.mapping.get(value) match {
                      case Some(ref) =>
                        c.subtypes.find(_.name.contains(ref.name)) match {
                          case Some(sub) => check(sub, j, path, named)
                          case None      => List(s"$path: discriminator '$value' maps to ${ref.name}, which is not a subtype")
                        }
                      case None => List(s"$path: discriminator value '$value' not in the documented mapping ${d.mapping.keySet}")
                    }
                  case _ => List(s"$path: discriminator field '${d.name.encodedName}' missing or not a string")
                }
              case other => List(s"$path: expected an object for a discriminated SCoproduct, got ${kind(other)}")
            }
          case None =>
            if (c.subtypes.exists(sub => check(sub, j, path, named).isEmpty)) Nil
            else List(s"$path: matches none of the ${c.subtypes.size} subtypes of an untagged SCoproduct")
        }
      case a: SArray[?, ?] =>
        j match {
          case arr: ujson.Arr => arr.value.toList.zipWithIndex.flatMap { case (e, i) => check(a.element, e, s"$path[$i]", named) }
          case other          => List(s"$path: expected an array for SArray, got ${kind(other)}")
        }
      case o: SOpenProduct[?, ?] =>
        j match {
          case obj: ujson.Obj =>
            val fixed = o.fields.map(f => f.name.encodedName -> f).toMap
            obj.value.toList.flatMap { case (k, v) =>
              fixed.get(k) match {
                case Some(f) => check(f.schema, v, s"$path.$k", named)
                case None    => check(o.valueSchema, v, s"$path.$k", named)
              }
            }
          case other => List(s"$path: expected an object for SOpenProduct, got ${kind(other)}")
        }
      case SString() =>
        j match {
          case ujson.Str(str) =>
            enumerations(s.validator).flatMap { e =>
              val allowed = e.possibleValues.map(v => e.encode.flatMap(_.apply(v)).map(_.toString).getOrElse(v.toString))
              if (allowed.contains(str)) Nil else List(s"$path: '$str' is not one of the documented enumeration values $allowed")
            }
          case other => List(s"$path: expected a string for SString, got ${kind(other)}")
        }
      case SInteger() | SNumber() =>
        j match {
          case _: ujson.Num => Nil
          case other        => List(s"$path: expected a number, got ${kind(other)}")
        }
      case SBoolean() =>
        j match {
          case _: ujson.Bool => Nil
          case other         => List(s"$path: expected a boolean, got ${kind(other)}")
        }
      case SBinary() | SDate() | SDateTime() =>
        j match {
          case _: ujson.Str => Nil
          case other        => List(s"$path: expected a string, got ${kind(other)}")
        }
    }

  private def enumerations(v: Validator[?]): List[Validator.Enumeration[Any]] = v match {
    case e: Validator.Enumeration[?] => List(e.asInstanceOf[Validator.Enumeration[Any]])
    case Validator.All(vs)           => vs.toList.flatMap(enumerations)
    case Validator.Any(vs)           => vs.toList.flatMap(enumerations)
    case _                           => Nil
  }

  private def kind(j: ujson.Value): String = j match {
    case _: ujson.Obj  => "object"
    case _: ujson.Arr  => "array"
    case _: ujson.Str  => "string"
    case _: ujson.Num  => "number"
    case _: ujson.Bool => "boolean"
    case ujson.Null    => "null"
  }
}
