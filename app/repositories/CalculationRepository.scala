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

package repositories

import models.{Calculation, Done}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Accumulators.{avg, sum}
import org.mongodb.scala.model.Aggregates._
import org.mongodb.scala.model._
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.play.http.logging.Mdc

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CalculationRepository @Inject()(mongoComponent: MongoComponent)
                                     (implicit ec: ExecutionContext)
  extends PlayMongoRepository[Calculation](
    collectionName = "calculations",
    mongoComponent = mongoComponent,
    domainFormat   = Calculation.format,
    extraCodecs    = Seq(
      Codecs.playFormatCodec(DistinctSessionIds.format),
      Codecs.playFormatCodec(TotalSavings.format),
      Codecs.playFormatCodec(AverageSalary.format)
    ),
    indexes        = Seq(
      IndexModel(
        Indexes.ascending("timestamp"),
        IndexOptions()
          .name("timestampIdx")
          .unique(false)
      ),
      IndexModel(
        Indexes.ascending("annualSalary"),
        IndexOptions().name("annualSalaryIdx")
      )
    )
  ) {

  override lazy val requiresTtlIndex: Boolean = false

  def save(calculation: Calculation): Future[Done] =
    Mdc.preservingMdc {
      collection.insertOne(calculation)
        .toFuture()
        .map(_ => Done)
    }

  def lastCalculation: Future[Option[Calculation]] =
    Mdc.preservingMdc {
      collection.find()
        .sort(Sorts.descending("timestamp"))
        .limit(1)
        .headOption()
    }

  def numberOfCalculations(from: Option[Instant] = None, to: Option[Instant] = None): Future[Long] =
    Mdc.preservingMdc {
      collection.countDocuments(timestampFilter(from, to)).head()
    }

  def numberOfUniqueSessions(from: Option[Instant] = None, to: Option[Instant] = None): Future[Long] =
    Mdc.preservingMdc {
      collection.aggregate[DistinctSessionIds](Seq(
        `match`(timestampFilter(from, to)),
        group("$sessionId"),
        count("distinctSessionIds"))
      ).headOption().map(_.map(_.distinctSessionIds).getOrElse(0))
    }

  def averageSalary(from: Option[Instant] = None, to: Option[Instant] = None): Future[Long] =
    Mdc.preservingMdc {
      collection.aggregate[AverageSalary](Seq(
        `match`(timestampFilter(from, to)),
        group(null, avg("averageSalary", "$annualSalary"))
      )).headOption().map(_.map(_.averageSalary.toLong).getOrElse(0))
    }

  def numberOfCalculationsWithNoSavings(from: Option[Instant] = None, to: Option[Instant] = None): Future[Long] =
    Mdc.preservingMdc {
      collection.countDocuments(
        Filters.and(
          timestampFilter(from, to),
          Filters.lt("annualSalary", 12572)
        )
      ).head()
    }

  def numberOfCalculationsWithMinimalSavings(from: Option[Instant] = None, to: Option[Instant] = None): Future[Long] =
    Mdc.preservingMdc {
      collection.countDocuments(
        Filters.and(
          timestampFilter(from, to),
          Filters.gte("annualSalary", 12572),
          Filters.lte("annualSalary", 12721)
        )
      ).head()
    }

  def totalSavingsDec23Apr24(from: Option[Instant] = None, to: Option[Instant] = None): Future[BigDecimal] =
    collection.aggregate[TotalSavings](Seq(
      `match`(timestampFilter(from, to)),
      group(null, sum("totalSavings", "$dec23Apr24AnnualSaving"))
    )).headOption().map(_.map(_.totalSavings).getOrElse(0))

  def totalSavingsAveragedBySessionDec23Apr24(from: Option[Instant] = None, to: Option[Instant] = None): Future[BigDecimal] =
    collection.aggregate[TotalSavings](Seq(
      `match`(timestampFilter(from, to)),
      group("$sessionId", avg("averageSavings", "$dec23Apr24AnnualSaving")),
      group(null, sum("totalSavings", "$averageSavings"))
    )).headOption().map(_.map(_.totalSavings).getOrElse(0))

  def totalSavingsMar24Apr24(from: Option[Instant] = None, to: Option[Instant] = None): Future[BigDecimal] =
    collection.aggregate[TotalSavings](Seq(
      `match`(timestampFilter(from, to)),
      group(null, sum("totalSavings", "$mar24Apr24AnnualSaving"))
    )).headOption().map(_.map(_.totalSavings).getOrElse(0))

  def totalSavingsAveragedBySessionMar24Apr24(from: Option[Instant] = None, to: Option[Instant] = None): Future[BigDecimal] =
    collection.aggregate[TotalSavings](Seq(
      `match`(timestampFilter(from, to)),
      group("$sessionId", avg("averageSavings", "$mar24Apr24AnnualSaving")),
      group(null, sum("totalSavings", "$averageSavings"))
    )).headOption().map(_.map(_.totalSavings).getOrElse(0))

  private def timestampFilter(from: Option[Instant] = None, to: Option[Instant] = None): Bson = {
    val fromFilter = from.map(Filters.gte("timestamp", _))
    val toFilter = to.map(Filters.lt("timestamp", _))
    val filters = (for (from <- fromFilter; to <- toFilter) yield Filters.and(from, to)) orElse fromFilter orElse toFilter
    filters.getOrElse(Filters.empty())
  }
}

final case class DistinctSessionIds(distinctSessionIds: Long)

object DistinctSessionIds {
  implicit lazy val format: Format[DistinctSessionIds] = Json.format
}

final case class TotalSavings(totalSavings: BigDecimal)

object TotalSavings {
  implicit lazy val format: Format[TotalSavings] = Json.format
}

final case class AverageSalary(averageSalary: BigDecimal)

object AverageSalary {
  implicit lazy val format: Format[AverageSalary] = Json.format
}
