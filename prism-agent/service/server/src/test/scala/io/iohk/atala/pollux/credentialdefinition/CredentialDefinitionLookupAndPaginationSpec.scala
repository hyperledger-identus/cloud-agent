package io.iohk.atala.pollux.credentialdefinition

import io.iohk.atala.container.util.MigrationAspects.migrate
import io.iohk.atala.pollux.credentialdefinition.controller.CredentialDefinitionController
import io.iohk.atala.pollux.credentialdefinition.http.CredentialDefinitionResponse
import io.iohk.atala.pollux.credentialdefinition.http.CredentialDefinitionResponsePage
import sttp.client3.DeserializationException
import sttp.client3.Response
import sttp.client3.UriContext
import sttp.client3.basicRequest
import sttp.client3.ziojson.*
import sttp.model.StatusCode
import sttp.model.Uri
import zio.*
import zio.json.EncoderOps
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*

object CredentialDefinitionLookupAndPaginationSpec
    extends ZIOSpecDefault
    with CredentialDefinitionTestTools
    with CredentialDefinitionGen:

  def fetchAllPages(
      uri: Uri
  ): ZIO[CredentialDefinitionController, Throwable, List[CredentialDefinitionResponsePage]] = {
    for {
      controller <- ZIO.service[CredentialDefinitionController]
      backend = httpBackend(controller)
      response: CredentialDefinitionResponsePageType <-
        for {
          response <- basicRequest
            .get(uri)
            .response(asJsonAlways[CredentialDefinitionResponsePage])
            .send(backend)
        } yield {
          println(response)
          response
        }
      firstPage <- ZIO.fromEither(response.body)
      otherPagesStream = zio.stream.ZStream
        .unfoldZIO[Any, Throwable, CredentialDefinitionResponsePage, CredentialDefinitionResponsePage](firstPage)(
          page =>
            page.next
              .map(n => uri"$n")
              .fold(
                ZIO.succeed(Option.empty[(CredentialDefinitionResponsePage, CredentialDefinitionResponsePage)])
              )(nextPageUri =>
                for {
                  nextPageResponse: CredentialDefinitionResponsePageType <-
                    basicRequest
                      .get(nextPageUri)
                      .response(asJsonAlways[CredentialDefinitionResponsePage])
                      .send(backend)
                  nextPage <- ZIO.fromEither(nextPageResponse.body)
                } yield Some((nextPage, nextPage))
              )
        )
      otherPages <- otherPagesStream.runCollect
        .fold(_ => List.empty[CredentialDefinitionResponsePage], success => success.toList)

    } yield List(firstPage) ++ otherPages
  }

  def spec = (
    credentialDefinitionPaginationSpec @@
      nondeterministic @@ sequential @@ timed @@ migrate(
        schema = "public",
        paths = "classpath:sql/pollux"
      )
  ).provideSomeLayerShared(
    mockManagedDIDServiceLayer.exactly(20).toLayer >+> testEnvironmentLayer
  )

  private val credentialDefinitionPaginationSpec = suite("credential-definition-registry pagination logic")(
    test("pagination of the first page with the empty query params") {
      for {
        _ <- deleteAllCredentialDefinitions
        controller <- ZIO.service[CredentialDefinitionController]
        backend = httpBackend(controller)

        inputs <- Generator.credentialDefinitionInput.runCollectN(10)
        _ <- inputs
          .map(in =>
            basicRequest
              .post(credentialDefinitionUriBase)
              .body(in.toJsonPretty)
              .response(asJsonAlways[CredentialDefinitionResponse])
              .send(backend)
          )
          .reduce((l, r) => l.flatMap(_ => r))

        response: CredentialDefinitionResponsePageType <- basicRequest
          .get(uri"$credentialDefinitionUriBase?offset=0&limit=4")
          .response(asJsonAlways[CredentialDefinitionResponsePage])
          .send(backend)

        credentialDefinitionPage <- ZIO.fromEither(response.body)

        itIsSuccessfulResponse = assert(response.code)(equalTo(StatusCode.Ok))
        itReturnedDefaultLimitOfItemsInOnePage = assert(credentialDefinitionPage.contents.length)(equalTo(4))
        nextPage_isNonEmpty = assertTrue(credentialDefinitionPage.next.nonEmpty)
        previousPage_isEmpty = assertTrue(credentialDefinitionPage.previous.isEmpty)
        self_equalsTo_theValidUri = assert(credentialDefinitionPage.self)(
          equalTo(s"$credentialDefinitionUriBase?offset=0&limit=4")
        )
        pageOf_equalTo_theValidUri = assert(credentialDefinitionPage.pageOf)(
          equalTo(credentialDefinitionUriBase.toString)
        )

      } yield itIsSuccessfulResponse &&
        itReturnedDefaultLimitOfItemsInOnePage &&
        nextPage_isNonEmpty &&
        previousPage_isEmpty &&
        self_equalsTo_theValidUri &&
        pageOf_equalTo_theValidUri
    },
    test("pagination and navigation over the pages") {
      for {
        _ <- deleteAllCredentialDefinitions
        inputs <- generateCredentialDefinitionsN(10)

        allPagesWithLimit1 <- fetchAllPages(uri"$credentialDefinitionUriBase?offset=0&limit=1")
        allPagesWithLimit10 <- fetchAllPages(uri"$credentialDefinitionUriBase?offset=0&limit=5")
        allPagesWithLimit15 <- fetchAllPages(uri"$credentialDefinitionUriBase?offset=0&limit=3")
      } yield assert(inputs.length)(equalTo(10)) &&
        assert(allPagesWithLimit1.length)(equalTo(10)) &&
        assert(allPagesWithLimit10.length)(equalTo(2)) &&
        assert(allPagesWithLimit15.length)(equalTo(4))
    }
  )
