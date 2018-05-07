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

package com.inland24.plantmon.services.database.repository

import com.inland24.plantmon.core.AppMetrics
import com.inland24.plantmon.models.PowerPlantFilter
import com.inland24.plantmon.services.database.models.PowerPlantRow

import scala.language.higherKinds

trait PowerPlantRepository[M[_]] {
  // Fetch operations
  def powerPlants(onlyActive: Boolean): M[Seq[PowerPlantRow]]
  def powerPlantsPaginated(filter: PowerPlantFilter): M[Seq[PowerPlantRow]]
  def powerPlant(id: Int): M[Option[PowerPlantRow]]

  // Create operations
  def newPowerPlant(powerPlantRow: PowerPlantRow): M[Int]

  // Update operations
  def updatePowerPlant(powerPlantRow: PowerPlantRow): M[Int]

  // Delete operations
  def deletePowerPlant(id: Int): M[Int]

  // Miscellaneous operations
  /**
    * This is a multi database call method that does the following:
    *
    * 1. Fetch the last sync date from Tenant table
    * 2. 
    * @param tenantId The tenant for which we need to run the update
    */
  def fetchUpdatesAndSyncDate(tenantId: Int)
  def updateSyncDate(tenantId: Int)


  def withTimerMetrics[T](fn: => T): T = {
    val context = AppMetrics.timer.time()
    try {
      fn
    } finally {
      context.close()
    }
  }
}
