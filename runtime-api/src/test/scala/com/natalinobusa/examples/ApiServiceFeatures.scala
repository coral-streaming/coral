package com.natalinobusa.examples

import org.scalatest.{GivenWhenThen, FeatureSpec}
import org.scalatest.Matchers._

// spray
import spray.testkit.ScalatestRouteTest
import spray.http.StatusCodes._

// json
import com.natalinobusa.examples.models.JsonConversions._

class ApiServiceFeatures extends FeatureSpec with GivenWhenThen with ScalatestRouteTest with ApiService {

  // connect the routing DSL to the test ActorSystem
  def actorRefFactory = system

  info("As an api user")
  info("I want to be able to request my API resources")

  feature("REST greetings ") {
    scenario("POST some transaction") {

      Given("The correct Resource definition")
      //val event = Transaction("abcd",123, "Amsterdam")

      Then("should match the with the response json")
//      Post("/api/in", event) ~> serviceRoute ~> check {
//        responseAs[Transaction] shouldBe event
//      }
    }

    scenario("No other method allowed, other than above") {
      Seq(Put, Options, Delete, Patch, Head).foreach(
        _("/api/greetings", "a plain text message") ~> sealRoute(serviceRoute) ~> check {
          status === MethodNotAllowed
        })
    }
  }
}
