package com.natalinobusa.examples.models

import spray.httpx.Json4sJacksonSupport

import org.json4s.Formats
import org.json4s.DefaultFormats

/* Used to mix in Spray's Marshalling Support with json4s */
object JsonConversions extends Json4sJacksonSupport {
  implicit def json4sJacksonFormats: Formats = DefaultFormats
}
