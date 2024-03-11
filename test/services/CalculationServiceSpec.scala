/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services

import models.{Calculation, CalculationRequest, Done}
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, Mockito}
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.CalculationRepository

import java.time.{Clock, Instant, LocalDate, ZoneId}
import scala.concurrent.Future

class CalculationServiceSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with BeforeAndAfterEach {

  private val mockRepository = mock[CalculationRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockRepository)
  }

  ".save" - {

    "must save a calculation" in {

      when(mockRepository.save(any())).thenReturn(Future.successful(Done))

      val fixedInstant: Instant = LocalDate.now.atStartOfDay(ZoneId.systemDefault).toInstant
      val clockAtFixedInstant: Clock = Clock.fixed(fixedInstant, ZoneId.systemDefault)

      val application =
        new GuiceApplicationBuilder()
          .overrides(
            bind[CalculationRepository].toInstance(mockRepository),
            bind[Clock].toInstance(clockAtFixedInstant)
          ).configure("crypto.key" -> "eTWbbFeb1TPUBE5vq6A+LUGhl3LVtZwHhzZggfLMjpc=")
          .build()

      val service = application.injector.instanceOf[CalculationService]

      val sessionId = "bar"
      val salary = BigDecimal(1)
      val dec23Nic = BigDecimal(2.2)
      val mar24Nic = BigDecimal(3.3)
      val apr24Nic = BigDecimal(4.4)
      val calculationRequest = CalculationRequest(salary, dec23Nic, mar24Nic, apr24Nic)

      val captor = ArgumentCaptor.forClass(classOf[Calculation])

      service.save(sessionId, calculationRequest).futureValue
      verify(mockRepository, times(1)).save(captor.capture())

      val calculation: Calculation = captor.getValue
      calculation.annualSalary mustEqual salary
      calculation.dec23EstimatedNic mustEqual dec23Nic
      calculation.mar24EstimatedNic mustEqual mar24Nic
      calculation.apr24EstimatedNic mustEqual apr24Nic
      calculation.timestamp mustEqual fixedInstant
    }
  }
}
