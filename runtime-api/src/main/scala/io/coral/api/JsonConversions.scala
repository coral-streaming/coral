package io.coral.api

import java.lang.reflect.InvocationTargetException

import org.json4s.{MappingException, DefaultFormats, Formats}
import spray.http.{HttpCharsets, HttpEntity, MediaTypes}
import spray.httpx.Json4sJacksonSupport
import spray.httpx.unmarshalling.Unmarshaller

/* Used to mix in Spray's Marshalling Support with json4s */
object JsonConversions extends Json4sJacksonSupport {
  implicit def json4sJacksonFormats: Formats = DefaultFormats

  implicit def jsonApiUnmarshaller[T: Manifest] =
    Unmarshaller[T](MediaTypes.`application/vnd.api+json`) {
      case x: HttpEntity.NonEmpty ⇒
        try serialization.read[T](x.asString(defaultCharset = HttpCharsets.`UTF-8`))
        catch {
          case MappingException("unknown error", ite: InvocationTargetException) ⇒ throw ite.getCause
        }
    }
}