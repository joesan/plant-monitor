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

package com.inland24.plantsim.core

import akka.actor._
import akka.pattern.pipe
import akka.util.Timeout
import com.inland24.plantsim.config.AppConfig
import com.inland24.plantsim.core.SupervisorActor.SupervisorEvents
import com.inland24.plantsim.models.PowerPlantConfig
import com.inland24.plantsim.models.PowerPlantConfig.{
  OnOffTypeConfig,
  RampUpTypeConfig
}
import com.inland24.plantsim.models.PowerPlantDBEvent.{
  PowerPlantCreateEvent,
  PowerPlantDeleteEvent,
  PowerPlantUpdateEvent
}
import com.inland24.plantsim.models.PowerPlantType.{OnOffType, RampUpType}
import com.inland24.plantsim.services.database.DBServiceActor
import com.inland24.plantsim.services.database.DBServiceActor.PowerPlantEventsSeq
import com.inland24.plantsim.streams.EventsStream
import monix.execution.{Ack, Scheduler}
import monix.execution.Ack.Continue
import monix.execution.cancelables.SingleAssignmentCancelable

import scala.concurrent.Future
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
  val cancelable = SingleAssignmentCancelable()

  // This is how we name our actors
  val simulatorActorNamePrefix = config.appName

  // The default timeout for all Ask's the Actor makes
  implicit val timeout = Timeout(5.seconds)

  // The DBServiceActor instance that is responsible for tracking changes to the PowerPlant table
  val dbServiceActor = context.actorOf(
    DBServiceActor.props(config.dbConfig, self),
    "plant-simulator-dbService")

  // The EventsStream Actor to which all our PowerPlant's will send Events and Alerts
  val eventsStream = context.actorOf(EventsStream.props(globalChannel))

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

  // ***********************************************************************************
  // Methods to Start and Stop PowerPlant Actor instances
  // ***********************************************************************************
  private def startPowerPlant(id: Long, cfg: PowerPlantConfig): Future[Ack] =
    cfg.powerPlantType match {
      case OnOffType =>
        log.info(s"Starting OnOffType PowerPlant with id $id")
        context.actorOf(
          OnOffTypeActor.props(
            OnOffTypeActor.Config(cfg.asInstanceOf[OnOffTypeConfig],
                                  Some(eventsStream))),
          s"$simulatorActorNamePrefix-$id"
        )
        log.info(s"Successfully started OnOffType PowerPlant with id $id")
        Continue

      case RampUpType =>
        log.info(s"Starting RampUpType PowerPlant with id $id")
        context.actorOf(
          RampUpTypeActor.props(
            RampUpTypeActor.Config(cfg.asInstanceOf[RampUpTypeConfig],
                                   Some(eventsStream))),
          s"$simulatorActorNamePrefix-$id"
        )
        log.info(s"Successfully started RampUpType PowerPlant with id $id")
        Continue

      case _ =>
        Continue
        Continue
    }

  def waitForStop(source: ActorRef): Receive = {
    case Terminated(actor) =>
      context.unwatch(actor)
      log.info(
        s"Actor Terminated message received for actor ${source.path.name}")
      // Now unstash all of the messages
      log.info(s"Un-stashing all messages")
      context.become(receive)
      unstashAll()

    case someDamnThing =>
      log.error(s"Unexpected message $someDamnThing :: " +
        s"received while waiting for an actor to be stopped => Stashing message")
      stash()
  }

  def waitForRestart(source: ActorRef,
      powerPlantCreateEvent: PowerPlantCreateEvent[PowerPlantConfig]): Receive = {
    case Terminated(actor) =>
      context.unwatch(actor)
      log.info(
        s"Actor Terminated message received for actor ${source.path.name}")
      self ! powerPlantCreateEvent
      // Now unstash all of the messages
      log.info(s"Un-stashing all messages")
      context.become(receive)
      unstashAll()

    case someDamnThing =>
      log.warning(s"Unexpected message $someDamnThing :: " +
        s"received while waiting for an actor to be re-started => Stashing message")
      stash()
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

      // Start the PowerPlant, and pipe the message to self
      startPowerPlant(id, powerPlantCfg).pipeTo(self)

    case PowerPlantUpdateEvent(id, powerPlantCfg) =>
      log.info(
        s"Sending PowerPlantUpdateEvent for PowerPlant => { Id = $id, type = ${powerPlantCfg.powerPlantType} } to Cluster Backend")

      context.child(s"$simulatorActorNamePrefix-$id") match {
        case Some(actorRef) =>
          context.watch(actorRef)
          // We first kill the child actor instance
          actorRef ! PoisonPill

          // We wait asynchronously until this Actor is re-started
          context.become(
            waitForRestart(
              actorRef,
              PowerPlantCreateEvent(id, powerPlantCfg)
            )
          )

        case None =>
          log.warning(
            s"No running actor instance found for id $id :: Creating a new instance")
          self ! PowerPlantCreateEvent(id, powerPlantCfg)
      }

    case PowerPlantDeleteEvent(id, powerPlantCfg) =>
      log.info(
        s"Sending PowerPlantDeleteEvent for PowerPlant => { Id = $id, type = ${powerPlantCfg.powerPlantType} } to Cluster Backend")

      context.child(s"$simulatorActorNamePrefix-$id") match {
        case Some(actorRef) =>
          context.watch(actorRef)
          context.become(waitForStop(actorRef))
          actorRef ! Kill

        case None =>
          log.warning(
            s"PowerPlantDeleteEvent # No running actor instance found for id $id")
      }
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
