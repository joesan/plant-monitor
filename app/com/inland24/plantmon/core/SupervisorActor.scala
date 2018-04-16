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

package com.inland24.plantmon.core

import akka.actor._
import akka.util.Timeout
import com.inland24.plantmon.config.AppConfig
import com.inland24.plantmon.core.SupervisorActor.SupervisorEvents
import com.inland24.plantmon.models.PowerPlantDBEvent.{
  PowerPlantCreateEvent,
  PowerPlantDeleteEvent,
  PowerPlantUpdateEvent
}
import com.inland24.plantmon.services.database.DBServiceActor
import com.inland24.plantmon.services.database.DBServiceActor.PowerPlantEventsSeq
import com.inland24.plantmon.streams.EventsStream
import monix.execution.Scheduler
import monix.execution.cancelables.SingleAssignmentCancelable

import scala.concurrent.duration._

/**
  * The SupervisorActor is initialized when bootstrapping
  * the application. Have a look at [[Bootstrap]] and [[AppBindings]]
  *
  * The actor starts it's life in the init method where
  * it performs the following:
  *
  * 1. Initializes all the streams
  * 2. Attaches subscribers to the streams
  * 3. Starts the child actors and watches them
  * 4. Re-starts the child actors when needed (in case of failures)
  */
// TODO: Get a reference to the HTTP service to the Akka Cluster Backend and push the events to this service
// which will then be forwarded to the Akka Cluster Backend
class SupervisorActor(
    config: AppConfig,
    globalChannel: PowerPlantEventObservable)(implicit s: Scheduler)
    extends Actor
    with ActorLogging
    with Stash {

  // We would use this to safely dispose any open connections
  private val cancelable = SingleAssignmentCancelable()

  // This is how we name our actors
  private val simulatorActorNamePrefix = config.appName

  // The default timeout for all Ask's the Actor makes
  private implicit val timeout: Timeout = Timeout(5.seconds)

  // The DBServiceActor instance that is responsible for tracking changes to the PowerPlant table
  private val dbServiceActor = context.actorOf(
    DBServiceActor.props(config.dbConfig, self),
    "plant-monitor-dbService")

  // The EventsStream Actor to which all our PowerPlant's will send Events and Alerts
  private val eventsStream = context.actorOf(EventsStream.props(globalChannel))

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 5.seconds) {
      case _: ActorKilledException =>
        SupervisorStrategy.Stop

      case e: Exception =>
        log.error("plant-simulator", e)
        SupervisorStrategy.Resume
    }

  override def preStart(): Unit = {
    super.preStart()
  }

  override def postStop(): Unit = {
    super.postStop()
    cancelable.cancel()
  }

  /**
    * The receive method expects a sequence of events regarding the PowerPlants
    * from the database. For each event, it then fires a HTTP request to a Load
    * Balancer that can then route it to one of the available clusters to either
    * Start, Stop or Update a PowerPlant Actor instance
    *
    *
    */
  def receive: Receive = {

    case Terminated(actorRef) =>
      context.unwatch(actorRef)

    case SupervisorEvents(events) =>
      log.info(
        s"SupervisorActor received new PowerPlantEvent events of size ${events.length}")
      events.foreach(event => self ! event)

    case PowerPlantCreateEvent(id, powerPlantCfg) =>
      log.info(
        s"Sending PowerPlantCreateEvent for PowerPlant => { Id = $id, type = ${powerPlantCfg.powerPlantType} } to Cluster Backend")

      // TODO: Send the Event to the HTTPService

    case PowerPlantUpdateEvent(id, powerPlantCfg) =>
      log.info(
        s"Sending PowerPlantUpdateEvent for PowerPlant => { Id = $id, type = ${powerPlantCfg.powerPlantType} } to Cluster Backend")

      // TODO: Send the Event to the HTTPService


    case PowerPlantDeleteEvent(id, powerPlantCfg) =>
      log.info(
        s"Sending PowerPlantDeleteEvent for PowerPlant => { Id = $id, type = ${powerPlantCfg.powerPlantType} } to Cluster Backend")

      // TODO: Send the Event to the HTTPService
  }
}
object SupervisorActor {

  sealed trait Message
  case object Init extends Message
  case class SupervisorEvents(events: PowerPlantEventsSeq)

  def props(cfg: AppConfig, globalChannel: PowerPlantEventObservable)(
      implicit s: Scheduler) =
    Props(new SupervisorActor(cfg, globalChannel)(s))
}
