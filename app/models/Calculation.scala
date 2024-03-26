/*
 * Copyright 2023 HM Revenue & Customs
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

package models

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.crypto.Scrambled
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

final case class Calculation(
                              sessionId: Scrambled,
                              annualSalary: BigDecimal,
                              dec23EstimatedNic: BigDecimal,
                              mar24EstimatedNic: BigDecimal,
                              apr24EstimatedNic: BigDecimal,
                              dec23Apr24AnnualSaving: BigDecimal,
                              mar24Apr24AnnualSaving: BigDecimal,
                              timestamp: Instant
                            )

object Calculation extends MongoJavatimeFormats.Implicits {

  private lazy val reads: Reads[Calculation] = (
    (__ \ "sessionId").read[String].map(Scrambled) and
    (__ \ "annualSalary").read[BigDecimal] and
    (__ \ "dec23EstimatedNic").read[BigDecimal] and
    (__ \ "mar24EstimatedNic").read[BigDecimal] and
    (__ \ "apr24EstimatedNic").read[BigDecimal] and
    (__ \ "dec23Apr24AnnualSaving").read[BigDecimal] and
    (__ \ "mar24Apr24AnnualSaving").read[BigDecimal] and
    (__ \ "timestamp").read[Instant]
  )(Calculation.apply _)

  private lazy val writes: OWrites[Calculation] = (
    (__ \ "sessionId").write[String] and
    (__ \ "annualSalary").write[BigDecimal] and
    (__ \ "dec23EstimatedNic").write[BigDecimal] and
    (__ \ "mar24EstimatedNic").write[BigDecimal] and
    (__ \ "apr24EstimatedNic").write[BigDecimal] and
    (__ \ "dec23Apr24AnnualSaving").write[BigDecimal] and
    (__ \ "mar24Apr24AnnualSaving").write[BigDecimal] and
    (__ \ "timestamp").write[Instant]
  )(c => (c.sessionId.value, c.annualSalary, c.dec23EstimatedNic, c.mar24EstimatedNic, c.apr24EstimatedNic, c.dec23Apr24AnnualSaving, c.mar24Apr24AnnualSaving, c.timestamp))

  implicit lazy val format: OFormat[Calculation] = OFormat(reads, writes)
}
