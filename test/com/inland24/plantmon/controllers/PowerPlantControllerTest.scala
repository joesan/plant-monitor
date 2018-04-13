/*
 * Copyright (c) 2017 joesan @ http://github.com/joesan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.inland24.plantsim.controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import com.inland24.plantsim.core.AppBindings
import com.inland24.plantsim.services.database.DBServiceSpec
import org.scalatest.{
  BeforeAndAfterAll,
  MustMatchers,
  OptionValues,
  WordSpecLike
}
import monix.execution.FutureUtils.extensions._
import monix.execution.Scheduler.Implicits.global

import scala.concurrent.Future
import org.scalatestplus.play._
import play.api.libs.json._
import play.api.mvc._
import play.api.test._
import play.api.test.Helpers._

import scala.util.{Failure, Success}

class PowerPlantControllerTest
    extends TestKit(ActorSystem("PowerPlantControllerTest"))
    with MustMatchers
    with OptionValues
    with WsScalaTestClient
    with WordSpecLike
    with Results
    with BeforeAndAfterAll
    with DBServiceSpec {

  val bindings = AppBindings.apply(system, ActorMaterializer())
  private val controllerComponents = stubControllerComponents()
  val controller = new PowerPlantController(bindings, controllerComponents)

  override def beforeAll(): Unit = {
    // 1. Set up the Schemas
    super.h2SchemaSetup()

    // 2. Populate the tables
    super.populateTables()
  }

  override def afterAll(): Unit = {
    super.h2SchemaDrop()
    TestKit.shutdownActorSystem(system)
  }

  // ApplicationConfigController test
  "ApplicationConfigController ## appConfig" should {
    "give the appropriate config back when asked" in {
      // We are using the application.test.conf (Look in the DBServiceSpec.scala)
      val result: Future[Result] =
        new ApplicationController(bindings.appConfig, controllerComponents).appConfig
          .apply(FakeRequest())
      val bodyText = contentAsJson(result)
      bodyText mustBe Json.parse(
        """
          |{
          |  "environment" : "test",
          |  "application" : "plant-simulator",
          |  "dbConfig" : {
          |    "databaseDriver" : "org.h2.Driver",
          |    "databaseUrl" : "jdbc:h2:mem:plant-simulator;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=false",
          |    "databaseUser" : "***********",
          |    "databasePass" : "***********"
          |  }
          |}
        """.stripMargin
      )
    }

    "fetch the JVM metrics" in {
      val result: Future[Result] =
        new ApplicationController(bindings.appConfig, controllerComponents).metrics
          .apply(FakeRequest())
      val bodyText = contentAsString(result)

      assert(bodyText.contains(""""hostname" : """))
    }
  }

  // PowerPlantDetails test
  "PowerPlantController ## powerPlantDetails" should {
    "fetch the details of a PowerPlant" in {
      val result: Future[Result] =
        controller.powerPlantDetails(101).apply(FakeRequest())
      contentAsJson(result) mustBe
        Json.parse("""
          |{
          |  "powerPlantId" : 101,
          |  "powerPlantName" : "joesan 1",
          |  "minPower" : 100,
          |  "maxPower" : 800,
          |  "rampPowerRate" : 20,
          |  "rampRateInSeconds" : 2,
          |  "powerPlantType" : "RampUpType"
          |}
        """.stripMargin)
    }

    "return a HTTP 404 for a non existing PowerPlant" in {
      val result: Future[Result] =
        controller.powerPlantDetails(1).apply(FakeRequest())
      val bodyText: String = contentAsString(result)
      bodyText mustBe "HTTP 404 :: PowerPlant with ID 1 not found"
    }
  }

  // Search PowerPlants test
  "PowerPlantController ## searchPowerPlants" should {
    val allActivePowerPlants =
      """
        |[{
        |   "powerPlantId":101,
        |   "powerPlantName":"joesan 1",
        |   "minPower":100,
        |   "maxPower":800,
        |   "rampPowerRate":20,
        |   "rampRateInSeconds":2,
        |   "powerPlantType":"RampUpType"
        |},
        |{
        |   "powerPlantId":102,
        |   "powerPlantName":"joesan 2",
        |   "minPower":200,
        |   "maxPower":1600,
        |   "powerPlantType":"OnOffType"
        |},
        |{
        |   "powerPlantId":103,
        |   "powerPlantName":"joesan 3",
        |   "minPower":300,
        |   "maxPower":2400,
        |   "rampPowerRate":20,
        |   "rampRateInSeconds":2,
        |   "powerPlantType":"RampUpType"
        |},
        |{
        |   "powerPlantId":104,
        |   "powerPlantName":"joesan 4",
        |   "minPower":400,
        |   "maxPower":3200,
        |   "powerPlantType":"OnOffType"
        |},
        |{
        |   "powerPlantId":105,
        |   "powerPlantName":"joesan 5",
        |   "minPower":500,
        |   "maxPower":4000,
        |   "rampPowerRate":20,
        |   "rampRateInSeconds":2,
        |   "powerPlantType":"RampUpType"
        |}]
      """.stripMargin

    "search all activePowerPlants" in {
      val result1: Future[Result] =
        controller
          .powerPlants(onlyActive = true, page = 1)
          .apply(FakeRequest())

      val result2: Future[Result] =
        controller
          .searchPowerPlants(onlyActive = Some(true), page = 1)
          .apply(FakeRequest())

      contentAsJson(result2) mustBe Json.parse(allActivePowerPlants)
      contentAsJson(result1) mustBe Json.parse(allActivePowerPlants)
    }

    "search PowerPlants only non active ones" in {
      val result1: Future[Result] =
        controller
          .powerPlants(onlyActive = false, page = 1)
          .apply(FakeRequest())
      contentAsString(result1) mustBe "[ ]"

      val result2: Future[Result] =
        controller
          .searchPowerPlants(onlyActive = Some(false), page = 1)
          .apply(FakeRequest())

      contentAsString(result2) mustBe "[ ]" // All the 5 PowerPlant's in the database are active
      contentAsString(result1) mustBe "[ ]" // All the 5 PowerPlant's in the database are active
    }

    "search all RampUpType active PowerPlant's" in {
      val result: Future[Result] =
        controller
          .searchPowerPlants(onlyActive = Some(true),
                             page = 1,
                             powerPlantType = Some("RampUpType"))
          .apply(FakeRequest())
      contentAsJson(result) mustBe Json.parse(
        """
          |[{
          |   "powerPlantId":101,
          |   "powerPlantName":"joesan 1",
          |   "minPower":100,
          |   "maxPower":800,
          |   "rampPowerRate":20,
          |   "rampRateInSeconds":2,
          |   "powerPlantType":"RampUpType"
          |},
          |{
          |   "powerPlantId":103,
          |   "powerPlantName":"joesan 3",
          |   "minPower":300,
          |   "maxPower":2400,
          |   "rampPowerRate":20,
          |   "rampRateInSeconds":2,
          |   "powerPlantType":"RampUpType"
          |},
          |{
          |   "powerPlantId":105,
          |   "powerPlantName":"joesan 5",
          |   "minPower":500,
          |   "maxPower":4000,
          |   "rampPowerRate":20,
          |   "rampRateInSeconds":2,
          |   "powerPlantType":"RampUpType"
          |}]
        """.stripMargin
      )
    }

    "search all OnOffType active PowerPlant's" in {
      val result: Future[Result] =
        controller
          .searchPowerPlants(onlyActive = Some(true),
                             page = 1,
                             powerPlantType = Some("OnOffType"))
          .apply(FakeRequest())
      contentAsJson(result) mustBe Json.parse(
        """
          |[
          |   {
          |      "powerPlantId":102,
          |      "powerPlantName":"joesan 2",
          |      "minPower":200,
          |      "maxPower":1600,
          |      "powerPlantType":"OnOffType"
          |   },
          |   {
          |      "powerPlantId":104,
          |      "powerPlantName":"joesan 4",
          |      "minPower":400,
          |      "maxPower":3200,
          |      "powerPlantType":"OnOffType"
          |   },
          |   {
          |      "powerPlantId":106,
          |      "powerPlantName":"joesan 6",
          |      "minPower":600,
          |      "maxPower":4800,
          |      "powerPlantType":"OnOffType"
          |   }
          |]
          |
          |
        """.stripMargin
      )
    }

    "search all UnknownType active PowerPlant's" in {
      val result: Future[Result] =
        controller
          .searchPowerPlants(onlyActive = Some(true),
                             page = 1,
                             powerPlantType = Some("SomeUnknownType"))
          .apply(FakeRequest())
      contentAsJson(result) mustBe Json.parse(allActivePowerPlants)
    }

    "search all active PowerPlant's with powerPlantName joesan" in {
      val result: Future[Result] =
        controller
          .searchPowerPlants(onlyActive = Some(true),
                             page = 1,
                             powerPlantName = Some("joesan"))
          .apply(FakeRequest())
      contentAsJson(result) mustBe Json.parse(allActivePowerPlants)
    }
  }

  // Create PowerPlant test
  "PowerPlantController ## createPowerPlant" should {
    "create a new PowerPlant successfully" in {
      // When creating a new PowerPlant, we expect the id to be set to 0
      val create =
        """
          |{
          |   "powerPlantId":0,
          |   "powerPlantName":"joesan new PowerPlant",
          |   "minPower":100,
          |   "maxPower":800,
          |   "rampPowerRate":20.0,
          |   "rampRateInSeconds":2,
          |   "powerPlantType":"RampUpType"
          |}
        """.stripMargin

      val expected =
        """
          |{
          |   "powerPlantId":1,
          |   "powerPlantName":"joesan new PowerPlant",
          |   "minPower":100,
          |   "maxPower":800,
          |   "rampPowerRate":20.0,
          |   "rampRateInSeconds":2,
          |   "powerPlantType":"RampUpType"
          |}
        """.stripMargin

      val result: Future[Result] =
        controller.createNewPowerPlant
          .apply(
            FakeRequest().withBody(Json.parse(create))
          )
      contentAsJson(result) mustBe Json.parse(expected)
    }

    "not create a new PowerPlant when the PowerPlant Id is not set to 0" in {
      // When creating a new PowerPlant, we expect the id to be set to 0
      val create =
        """
          |{
          |   "powerPlantId":1,
          |   "powerPlantName":"joesan new PowerPlant",
          |   "minPower":100,
          |   "maxPower":800,
          |   "rampPowerRate":20.0,
          |   "rampRateInSeconds":2,
          |   "powerPlantType":"RampUpType"
          |}
        """.stripMargin

      val expected =
        """{"message":"invalid PowerPlantConfig! Please set the id of the Powerplant to 0 for create new PowerPlant"}"""

      val result: Future[Result] =
        controller.createNewPowerPlant
          .apply(
            FakeRequest().withBody(Json.parse(create))
          )
      contentAsJson(result) mustBe Json.parse(expected)
    }

    "not create a new PowerPlant for an Unknown PowerPlantType Config" in {
      // When creating a new PowerPlant, we expect the id to be set to 0
      val create =
        """
          |{
          |   "powerPlantId":0,
          |   "powerPlantName":"joesan new PowerPlant",
          |   "minPower":100,
          |   "maxPower":800,
          |   "rampPowerRate":20.0,
          |   "rampRateInSeconds":2,
          |   "powerPlantType":"UnknownType"
          |}
        """.stripMargin

      val expected =
        """{"message":"Invalid PowerPlantConfig List((/powerPlantType,List(JsonValidationError(List(Invalid PowerPlantType UnknownType. Should be one of RampUpType or OnOffType),WrappedArray()))))"}""".stripMargin

      val result: Future[Result] =
        controller.createNewPowerPlant
          .apply(
            FakeRequest().withBody(Json.parse(create))
          )
      contentAsJson(result) mustBe Json.parse(expected)
    }
  }

  // Update PowerPlant test
  "PowerPlantController ## updatePowerPlant" should {
    "update an active PowerPlant successfully" in {
      // We are updating the PowerPlant with id = 101, We just change its name
      val jsBody =
        """
          |{
          |   "powerPlantId":101,
          |   "powerPlantName":"joesan 1 updated",
          |   "minPower":100,
          |   "maxPower":800,
          |   "rampPowerRate":20.0,
          |   "rampRateInSeconds":2,
          |   "powerPlantType":"RampUpType"
          |}
        """.stripMargin
      val result: Future[Result] =
        controller
          .updatePowerPlant(101)
          .apply(
            FakeRequest().withBody(Json.parse(jsBody))
          )
      contentAsJson(result) mustBe Json.parse(jsBody)
    }

    "not update for an invalid PowerPlantConfig JSON" in {
      // We are updating the PowerPlant with id = invalidId, Notice that the powerPlantId is invalid
      val jsBody =
        """
          |{
          |   "powerPlantId":"invalidId",
          |   "powerPlantName":"joesan 1",
          |   "minPower":100,
          |   "maxPower":800,
          |   "rampPowerRate":20.0,
          |   "rampRateInSeconds": 2,
          |   "powerPlantType":"RampUpType"
          |}
        """.stripMargin

      val result: Future[Result] =
        controller
          .updatePowerPlant(101)
          .apply(
            FakeRequest().withBody(Json.parse(jsBody))
          )
      result.materialize.map {
        case Success(suck) =>
          assert(suck.header.status === BAD_REQUEST)
        case Failure(_) =>
          fail(
            "Unexpected test failure when Updating a PowerPlant! Please Analyze!")
      }
    }

    "not update for a PowerPlant that does not exist in the database" in {
      // We are updating the PowerPlant with id = 23001, which does not exist
      val jsBody =
        """
          |{
          |   "powerPlantId":23001,
          |   "powerPlantName":"joesan 1",
          |   "minPower":100,
          |   "maxPower":800,
          |   "rampPowerRate":20.0,
          |   "rampRateInSeconds": 2,
          |   "powerPlantType":"RampUpType"
          |}
        """.stripMargin

      val result: Future[Result] =
        controller
          .updatePowerPlant(23001)
          .apply(
            FakeRequest().withBody(Json.parse(jsBody))
          )
      result.materialize.map {
        case Success(suck) =>
          assert(suck.header.status === BAD_REQUEST)
        case Failure(_) =>
          fail(
            "Unexpected test failure when Updating a PowerPlant! Please Analyze!")
      }
    }
  }
}
