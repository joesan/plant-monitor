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

package com.inland24.plantmon.services.database.repository.impl

import com.inland24.plantmon.config.DBConfig
import com.inland24.plantmon.models.PowerPlantFilter
import com.inland24.plantmon.services.database.models.PowerPlantRow
import com.inland24.plantmon.services.database.repository.{
  DBSchema,
  PowerPlantRepository
}
import monix.eval.Task

import scala.concurrent.ExecutionContext

/**
  * Implementation of all database related services. Uses the Monix Task
  * for asynchronous execution
  * @param dbConfig The underlying database configuration
  * @param ec The Thread pool to operate on
  */
class PowerPlantRepoAsTask(dbConfig: DBConfig)(implicit ec: ExecutionContext)
    extends PowerPlantRepository[Task] { self =>

  private val schema = DBSchema(dbConfig.slickDriver)
  private val database = dbConfig.database
  private val recordsPerPage = dbConfig.recordCountPerPage

  /** Note: These imports should be here! Do not move it */
  import schema._
  import schema.driver.api._

  def powerPlants(
      fetchOnlyActive: Boolean = false): Task[Seq[PowerPlantRow]] = {
    val query =
      if (fetchOnlyActive)
        PowerPlantTable.activePowerPlants
      else
        PowerPlantTable.all

    withTimerMetrics(
      Task.deferFuture(database.run(query.result))
    )
  }

  def offset(pageNumber: Int): (Int, Int) =
    (pageNumber * recordsPerPage - recordsPerPage, pageNumber * recordsPerPage)

  // fetch the PowerPlants based on the Search criteria
  def powerPlantsPaginated(
      filter: PowerPlantFilter): Task[Seq[PowerPlantRow]] = {
    val (from, to) = offset(filter.pageNumber)
    val query = PowerPlantTable.powerPlantsFor(filter.powerPlantType,
                                               filter.orgName,
                                               filter.onlyActive)
    withTimerMetrics(
      Task.deferFuture(database.run(query.drop(from).take(to).result))
    )
  }

  def powerPlant(id: Int): Task[Option[PowerPlantRow]] = {
    withTimerMetrics(
      Task.deferFuture(
        database.run(PowerPlantTable.powerPlantById(id).result.headOption))
    )
  }

  def newPowerPlant(powerPlantRow: PowerPlantRow): Task[Int] = {
    withTimerMetrics(
      Task.deferFuture(database.run(PowerPlantTable.all += powerPlantRow))
    )
  }

  override def updatePowerPlant(powerPlantRow: PowerPlantRow): Task[Int] = {
    withTimerMetrics(
      Task.deferFuture(
        database.run(
          PowerPlantTable.all
            .filter(_.id === powerPlantRow.id)
            .update(powerPlantRow)
        )
      )
    )
  }

  // Delete in our case means, setting the PowerPlant inactive
  override def deletePowerPlant(id: Int): Task[Int] = {
    withTimerMetrics(
      Task.deferFuture(
        database.run(
          PowerPlantTable.deActivatePowerPlant(id)
        )
      )
    )
  }

  override def fetchUpdatesAndSyncDate(tenantId: Int): Unit = {

  }

  override def updateSyncDate(tenantId: Int): Unit = ???
}
