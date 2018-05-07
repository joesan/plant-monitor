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

package com.inland24.plantmon.services.database

import cats.Monad
import cats.syntax.all._
import com.inland24.plantmon.models.PowerPlantConfig.{
  OnOffTypeConfig,
  RampUpTypeConfig
}
import com.inland24.plantmon.models.PowerPlantType.{OnOffType, RampUpType}
import com.inland24.plantmon.models.{
  PowerPlantConfig,
  PowerPlantFilter,
  toPowerPlantRow
}
import com.inland24.plantmon.services.database.models.PowerPlantRow
import com.inland24.plantmon.services.database.repository.PowerPlantRepository

import scala.language.higherKinds

class PowerPlantService[M[_]: Monad](powerPlantRepo: PowerPlantRepository[M]) {

  def updatePowerPlant(
      powerPlantCfg: PowerPlantConfig): M[Either[String, PowerPlantConfig]] = {
    powerPlantRepo.powerPlant(powerPlantCfg.id).flatMap {
      case Some(_) =>
        toPowerPlantRow(powerPlantCfg) match {
          case Some(newPowerPlantRow) =>
            powerPlantRepo
              .updatePowerPlant(newPowerPlantRow)
              .map(_ => Right(powerPlantCfg))
          case None =>
            implicitly[Monad[M]].pure(Left(s"Invalid $powerPlantCfg"))
        }
      case None =>
        implicitly[Monad[M]].pure(
          Left(s"PowerPlant not found for the given id ${powerPlantCfg.id}"))
    }
  }

  def searchPowerPlants(filter: PowerPlantFilter): M[Seq[PowerPlantRow]] = {
    powerPlantRepo.powerPlantsPaginated(filter)
  }

  def fetchAllPowerPlants(
      onlyActive: Boolean = false): M[Seq[PowerPlantRow]] = {
    powerPlantRepo.powerPlants(onlyActive)
  }

  def fetchPowerPlantsAndSyncUpdateTime(
    onlyActive: Boolean = true): M[Seq[PowerPlantRow]] = {
    // 1. Fetch the last fetch time from the chronometer table

    // 2. Using the last fetch time, get the PowerPlant entries accordingly

    // 3. Update the current time as the last fetch time
  }

  def powerPlantById(id: Int): M[Option[PowerPlantRow]] = {
    powerPlantRepo.powerPlantById(id)
  }

  def createNewPowerPlant(
      cfg: PowerPlantConfig): M[Either[String, PowerPlantConfig]] = {
    toPowerPlantRow(cfg) match {
      case Some(powerPlantRow) =>
        powerPlantRepo
          .newPowerPlant(powerPlantRow)
          .map(newId =>
            cfg.powerPlantType match {
              case RampUpType =>
                Right(cfg.asInstanceOf[RampUpTypeConfig].copy(id = newId))
              case OnOffType =>
                Right(cfg.asInstanceOf[OnOffTypeConfig].copy(id = newId))
              case _ => Right(cfg)
          })
      case None =>
        implicitly[Monad[M]].pure(
          Left(s"Invalid Configuration $cfg when creating a new PowerPlant"))
    }
  }
}
