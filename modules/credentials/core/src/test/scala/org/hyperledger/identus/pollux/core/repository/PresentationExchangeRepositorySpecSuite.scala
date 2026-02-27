package org.hyperledger.identus.pollux.core.repository

import org.hyperledger.identus.pollux.prex.PresentationDefinition
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.{durationInt, ZIO, ZLayer}
import zio.json.{DecoderOps, DeriveJsonDecoder, JsonDecoder}
import zio.test.*
import zio.test.Assertion.*

import java.util.UUID
import scala.io.Source
import scala.util.Using

object PresentationExchangeRepositorySpecSuite {
  final case class ExampleTransportEnvelope(presentation_definition: PresentationDefinition)
  given JsonDecoder[ExampleTransportEnvelope] = DeriveJsonDecoder.gen

  private val loadPd =
    ZIO
      .fromTry(Using(Source.fromResource("pd/minimal_example.json"))(_.mkString))
      .flatMap(json => ZIO.fromEither(json.fromJson[ExampleTransportEnvelope]))
      .map(_.presentation_definition)

  val testSuite = suite("CRUD operations")(
    test("createPresentationDefinition creates a new record in DB") {
      for {
        repo <- ZIO.service[PresentationExchangeRepository]
        pd <- loadPd
        _ <- repo.createPresentationDefinition(pd)
        maybePd <- repo.findPresentationDefinition(UUID.fromString(pd.id))
      } yield assert(maybePd)(isSome(equalTo(pd)))
    },
    test("createPresentationDefinition prevents creation of 2 record with the same id") {
      for {
        repo <- ZIO.service[PresentationExchangeRepository]
        pd <- loadPd
        _ <- repo.createPresentationDefinition(pd)
        exit <- repo.createPresentationDefinition(pd).exit
      } yield assert(exit)(dies(anything))
    },
    test("createPresentationDefinition create a record with non-null optional fields") {
      for {
        repo <- ZIO.service[PresentationExchangeRepository]
        pd <- loadPd.map(
          _.copy(
            name = Some("name"),
            purpose = Some("purpose")
          )
        )
        _ <- repo.createPresentationDefinition(pd)
        maybePd <- repo.findPresentationDefinition(UUID.fromString(pd.id))
      } yield assert(maybePd)(isSome(equalTo(pd)))
    },
    test("findPresentationDefinition returns None for non-existing record") {
      for {
        repo <- ZIO.service[PresentationExchangeRepository]
        maybePd <- repo.findPresentationDefinition(UUID.randomUUID())
        _ <- repo.listPresentationDefinition()
      } yield assert(maybePd)(isNone)
    },
    test("listPresentationDefinition returns empty list when no records exist") {
      for {
        repo <- ZIO.service[PresentationExchangeRepository]
        result <- repo.listPresentationDefinition()
        (pdsList, count) = result
      } yield assert(pdsList)(isEmpty) && assert(count)(isZero)
    },
    test("listPresentationDefinition returns all records") {
      for {
        repo <- ZIO.service[PresentationExchangeRepository]
        pd <- loadPd
        pds = (1 to 5).map(_ => pd.copy(id = UUID.randomUUID().toString()))
        _ <- ZIO.foreach(pds)(repo.createPresentationDefinition)
        result <- repo.listPresentationDefinition()
        (pdsList, count) = result
      } yield assert(pdsList)(equalTo(pds)) && assert(count)(equalTo(5))
    },
    test("listPresentationDefinition return records respecting limit and offset") {
      for {
        repo <- ZIO.service[PresentationExchangeRepository]
        pd <- loadPd
        pds = (1 to 10).map(_ => pd.copy(id = UUID.randomUUID().toString()))
        _ <- ZIO.foreach(pds)(pd => TestClock.adjust(1.second) *> repo.createPresentationDefinition(pd))
        result1 <- repo.listPresentationDefinition(offset = Some(0), limit = Some(2))
        (pdsList1, count1) = result1
        result2 <- repo.listPresentationDefinition(offset = Some(2), limit = Some(3))
        (pdsList2, count2) = result2
      } yield assert(pdsList1)(equalTo(pds.drop(0).take(2))) &&
        assert(count1)(equalTo(10)) &&
        assert(pdsList2)(equalTo(pds.drop(2).take(3))) &&
        assert(count2)(equalTo(10))
    }
  ).provideSomeLayer(ZLayer.succeed(WalletAccessContext(WalletId.random)))

  val multitenantTestSuite = suite("multi-tenant CRUD operations")(
    test("do not see PresentationDefinition outside of the wallet when listing") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
      val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
      for {
        repo <- ZIO.service[PresentationExchangeRepository]
        pd <- loadPd
        pds1 = (1 to 3).map(_ => pd.copy(id = UUID.randomUUID().toString()))
        pds2 = (1 to 4).map(_ => pd.copy(id = UUID.randomUUID().toString()))
        _ <- ZIO.foreach(pds1)(pd =>
          TestClock.adjust(1.second) *>
            repo
              .createPresentationDefinition(pd)
              .provide(wallet1)
        )
        _ <- ZIO.foreach(pds2)(pd =>
          TestClock.adjust(1.second) *>
            repo
              .createPresentationDefinition(pd)
              .provide(wallet2)
        )
        pdsList1 <- repo
          .listPresentationDefinition(offset = Some(0), limit = Some(100))
          .provide(wallet1)
        pdsList2 <- repo
          .listPresentationDefinition(offset = Some(0), limit = Some(100))
          .provide(wallet2)
      } yield assert(pdsList1._1)(equalTo(pds1)) &&
        assert(pdsList2._1)(equalTo(pds2))
    },
    test("can read any presentation-definition by id from any wallet") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
      val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
      for {
        repo <- ZIO.service[PresentationExchangeRepository]
        pd <- loadPd
        pd1 = pd.copy(id = UUID.randomUUID().toString())
        pd2 = pd.copy(id = UUID.randomUUID().toString())
        _ <- repo.createPresentationDefinition(pd1).provide(wallet1)
        _ <- repo.createPresentationDefinition(pd2).provide(wallet2)
        maybePd1 <- repo.findPresentationDefinition(UUID.fromString(pd1.id))
        maybePd2 <- repo.findPresentationDefinition(UUID.fromString(pd2.id))
      } yield assert(maybePd1)(isSome(equalTo(pd1))) &&
        assert(maybePd2)(isSome(equalTo(pd2)))
    }
  )
}
