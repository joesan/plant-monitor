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

package com.inland24.plantsim.models

sealed trait PowerPlantState
object PowerPlantState {
  case object Init extends PowerPlantState
  case object RampUp extends PowerPlantState
  case object RampDown extends PowerPlantState
  case object Active extends PowerPlantState
  case object ReturnToNormal extends PowerPlantState
  case object OutOfService extends PowerPlantState
  case object ReturnToService extends PowerPlantState
  case object UnAvailable extends PowerPlantState
  case object Dispatched extends PowerPlantState
}
