package io.coral.api

import org.json4s.{DefaultFormats, Formats}
import spray.httpx.Json4sJacksonSupport

/* Used to mix in Spray's Marshalling Support with json4s */
object JsonConversions extends Json4sJacksonSupport {
	implicit def json4sJacksonFormats: Formats = DefaultFormats
}

