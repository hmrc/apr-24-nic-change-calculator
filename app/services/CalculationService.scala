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
import play.api.Configuration
import repositories.CalculationRepository
import uk.gov.hmrc.crypto.{OnewayCryptoFactory, PlainText}

import java.time.{Clock, Instant}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class CalculationService @Inject()(
                                    repository: CalculationRepository,
                                    configuration: Configuration,
                                    clock: Clock
                                  )(implicit ec: ExecutionContext) {

  def save(sessionId: String, calculationRequest: CalculationRequest): Future[Done] = {

    val crypto = OnewayCryptoFactory.shaCryptoFromConfig("crypto", configuration.underlying)
    val keyedAndHashedSessionId = crypto.hash(PlainText(sessionId))

    val calculation =
      Calculation(
        sessionId              = keyedAndHashedSessionId,
        annualSalary           = calculationRequest.annualSalary,
        dec23EstimatedNic      = calculationRequest.dec23EstimatedNic,
        mar24EstimatedNic      = calculationRequest.mar24EstimatedNic,
        apr24EstimatedNic      = calculationRequest.apr24EstimatedNic,
        dec23Apr24AnnualSaving = calculationRequest.dec23Apr24AnnualSaving,
        mar24Apr24AnnualSaving = calculationRequest.mar24Apr24AnnualSaving,
        timestamp              = Instant.now(clock)
      )

    repository.save(calculation)
  }

  def summary(from: Option[Instant] = None, to: Option[Instant] = None): Future[CalculationSummaryData] =
    for {
      numberOfCalculations <- repository.numberOfCalculations(from, to)
      numberOfUniqueSessions <- repository.numberOfUniqueSessions(from, to)
      numberOfCalculationsWithNoSavings <- repository.numberOfCalculationsWithNoSavings(from, to)
      numberOfCalculationsWithMinimalSavings <- repository.numberOfCalculationsWithMinimalSavings(from, to)
      totalSavingsDec23Apr24 <- repository.totalSavingsDec23Apr24(from, to)
      totalSavingsMar24Apr24 <- repository.totalSavingsMar24Apr24(from, to)
      totalSavingsDec23Apr24AveragedBySession <- repository.totalSavingsAveragedBySessionDec23Apr24(from, to)
      totalSavingsMar24Apr24AveragedBySession <- repository.totalSavingsAveragedBySessionMar24Apr24(from, to)
      averageSalary <- repository.averageSalary(from, to)
    } yield CalculationSummaryData(
      from = from,
      to = to,
      numberOfCalculations = numberOfCalculations,
      numberOfUniqueSessions = numberOfUniqueSessions,
      numberOfCalculationsWithNoSavings = numberOfCalculationsWithNoSavings,
      numberOfCalculationsWithMinimalSavings = numberOfCalculationsWithMinimalSavings,
      totalSavingsDec23Apr24 = totalSavingsDec23Apr24,
      totalSavingsMar24Apr24 = totalSavingsMar24Apr24,
      totalSavingsAveragedBySessionDec23Apr24 = totalSavingsDec23Apr24AveragedBySession,
      totalSavingsAveragedBySessionMar24Apr24 = totalSavingsMar24Apr24AveragedBySession,
      averageSalary = averageSalary
    )
}
