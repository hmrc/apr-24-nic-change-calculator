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

import models.{Calculation, CalculationRequest, CalculationSummaryData, Done}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.{ArgumentCaptor, Mockito}
import org.mockito.Mockito.{never, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.running
import repositories.CalculationRepository

import java.time.{Clock, Instant, LocalDate, ZoneId, ZoneOffset}
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
      val dec23Saving = BigDecimal(5.5)
      val mar24Saving = BigDecimal(6.6)
      val calculationRequest = CalculationRequest(salary, dec23Nic, mar24Nic, apr24Nic, dec23Saving, mar24Saving)

      val captor = ArgumentCaptor.forClass(classOf[Calculation])

      service.save(sessionId, calculationRequest).futureValue
      verify(mockRepository, times(1)).save(captor.capture())

      val calculation: Calculation = captor.getValue
      calculation.annualSalary mustEqual salary
      calculation.dec23EstimatedNic mustEqual dec23Nic
      calculation.mar24EstimatedNic mustEqual mar24Nic
      calculation.apr24EstimatedNic mustEqual apr24Nic
      calculation.dec23Apr24AnnualSaving mustEqual dec23Saving
      calculation.mar24Apr24AnnualSaving mustEqual mar24Saving
      calculation.timestamp mustEqual fixedInstant
    }
  }

  ".summary" - {

    val application =
      new GuiceApplicationBuilder()
        .overrides(
          bind[CalculationRepository].toInstance(mockRepository),
        )
        .build()

    val service = application.injector.instanceOf[CalculationService]

    val from = LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
    val to = LocalDate.of(2025, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)

    val summaryData = CalculationSummaryData(
      from = Some(from),
      to = Some(to),
      numberOfCalculations = 1000,
      numberOfUniqueSessions = 500,
      numberOfCalculationsWithNoSavings = 300,
      numberOfCalculationsWithMinimalSavings = 100,
      averageSalary = 15000
    )

    "must return a summary" in running(application) {

      when(mockRepository.numberOfCalculations(any(), any())).thenReturn(Future.successful(1000))
      when(mockRepository.numberOfUniqueSessions(any(), any())).thenReturn(Future.successful(500))
      when(mockRepository.numberOfCalculationsWithNoSavings(any(), any())).thenReturn(Future.successful(300))
      when(mockRepository.numberOfCalculationsWithMinimalSavings(any(), any())).thenReturn(Future.successful(100))
      when(mockRepository.averageSalary(any(), any())).thenReturn(Future.successful(15000))

      service.summary(Some(from), Some(to)).futureValue mustEqual summaryData

      verify(mockRepository).numberOfCalculations(eqTo(Some(from)), eqTo(Some(to)))
      verify(mockRepository).numberOfUniqueSessions(eqTo(Some(from)), eqTo(Some(to)))
      verify(mockRepository).numberOfCalculationsWithNoSavings(eqTo(Some(from)), eqTo(Some(to)))
      verify(mockRepository).numberOfCalculationsWithMinimalSavings(eqTo(Some(from)), eqTo(Some(to)))
      verify(mockRepository).averageSalary(eqTo(Some(from)), eqTo(Some(to)))
    }

    "must fail when the repository fails to return the number of calculations" in running(application) {

      when(mockRepository.numberOfCalculations(any(), any())).thenReturn(Future.failed(new RuntimeException()))

      service.summary(Some(from), Some(to)).failed.futureValue

      verify(mockRepository, never()).numberOfUniqueSessions(eqTo(Some(from)), eqTo(Some(to)))
      verify(mockRepository, never()).numberOfCalculationsWithNoSavings(eqTo(Some(from)), eqTo(Some(to)))
      verify(mockRepository, never()).numberOfCalculationsWithMinimalSavings(eqTo(Some(from)), eqTo(Some(to)))
      verify(mockRepository, never()).averageSalary(eqTo(Some(from)), eqTo(Some(to)))
    }

    "must fail when the repository fails to return the number of unique sessions" in running(application) {

      when(mockRepository.numberOfCalculations(any(), any())).thenReturn(Future.successful(1000))
      when(mockRepository.numberOfUniqueSessions(any(), any())).thenReturn(Future.failed(new RuntimeException()))

      service.summary(Some(from), Some(to)).failed.futureValue

      verify(mockRepository, never()).numberOfCalculationsWithNoSavings(eqTo(Some(from)), eqTo(Some(to)))
      verify(mockRepository, never()).numberOfCalculationsWithMinimalSavings(eqTo(Some(from)), eqTo(Some(to)))
      verify(mockRepository, never()).averageSalary(eqTo(Some(from)), eqTo(Some(to)))
    }

    "must fail when the repository fails to return the number of calculations with no savings" in running(application) {

      when(mockRepository.numberOfCalculations(any(), any())).thenReturn(Future.successful(1000))
      when(mockRepository.numberOfUniqueSessions(any(), any())).thenReturn(Future.successful(500))
      when(mockRepository.numberOfCalculationsWithNoSavings(any(), any())).thenReturn(Future.failed(new RuntimeException()))

      service.summary(Some(from), Some(to)).failed.futureValue

      verify(mockRepository, never()).numberOfCalculationsWithMinimalSavings(eqTo(Some(from)), eqTo(Some(to)))
      verify(mockRepository, never()).averageSalary(eqTo(Some(from)), eqTo(Some(to)))
    }

    "must fail when the repository fails to return the number of calculations with minimal savings" in running(application) {

      when(mockRepository.numberOfCalculations(any(), any())).thenReturn(Future.successful(1000))
      when(mockRepository.numberOfUniqueSessions(any(), any())).thenReturn(Future.successful(500))
      when(mockRepository.numberOfCalculationsWithNoSavings(any(), any())).thenReturn(Future.successful(300))
      when(mockRepository.numberOfCalculationsWithMinimalSavings(any(), any())).thenReturn(Future.failed(new RuntimeException()))

      service.summary(Some(from), Some(to)).failed.futureValue

      verify(mockRepository, never()).averageSalary(eqTo(Some(from)), eqTo(Some(to)))
    }

    "must fail when the repository fails to return the average salary" in running(application) {

      when(mockRepository.numberOfCalculations(any(), any())).thenReturn(Future.successful(1000))
      when(mockRepository.numberOfUniqueSessions(any(), any())).thenReturn(Future.successful(500))
      when(mockRepository.numberOfCalculationsWithNoSavings(any(), any())).thenReturn(Future.successful(300))
      when(mockRepository.numberOfCalculationsWithMinimalSavings(any(), any())).thenReturn(Future.successful(100))
      when(mockRepository.averageSalary(any(), any())).thenReturn(Future.failed(new RuntimeException()))

      service.summary(Some(from), Some(to)).failed.futureValue
    }
  }
}
