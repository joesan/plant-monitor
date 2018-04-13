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

package com.inland24.plantsim.config

import org.scalatest.FlatSpec

class AppConfigTest extends FlatSpec {

  private def clearSystemProperty() = {
    System.clearProperty("config.file")
    System.clearProperty("ENV")
    System.clearProperty("env")
  }

  private def setSystemProperty(key: String, value: String) = {
    System.setProperty(key, value)
  }

  val environments = Seq("default", "dev", "test")

  environments foreach { env =>
    "AppConfig # load" should s"load the configuration for $env environment" in {
      // set the environment
      clearSystemProperty()
      if (env != "default")
        setSystemProperty("env", env)

      // load the AppConfig
      val appConfig = AppConfig.load()

      // test expectations
      env match {
        case "dev" =>
          appConfig.dbConfig.slickDriver.toString() === "slick.jdbc.H2Profile$"
        case "test" =>
          appConfig.dbConfig.slickDriver.toString() === "slick.jdbc.H2Profile$"
        case _ =>
          appConfig.dbConfig.slickDriver.toString() === "slick.jdbc.H2Profile$"
      }
      assert(appConfig.environment === env)
      assert(appConfig.appName === "plant-simulator")
    }
  }
}
