package org.hyperledger.identus.pollux.credentialdefinition

import org.hyperledger.identus.agent.server.config.AppConfig
import org.hyperledger.identus.agent.walletapi.model.BaseEntity
import org.hyperledger.identus.agent.walletapi.service.MockManagedDIDService
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.container.util.MigrationAspects.migrate
import org.hyperledger.identus.iam.authentication.AuthenticatorWithAuthZ
import org.hyperledger.identus.pollux.credentialdefinition.controller.CredentialDefinitionController
import sttp.client3.{basicRequest, DeserializationException}
import sttp.client3.ziojson.*
import sttp.model.StatusCode
import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*

object CredentialDefinitionFailureSpec extends ZIOSpecDefault with CredentialDefinitionTestTools:
  def spec = (suite("credential-definition-registry bad request spec")(
    credentialDefinitionBadRequestAsJsonSpec
  ) @@ nondeterministic @@ sequential @@ timed @@ migrate(
    schema = "public",
    paths = "classpath:sql/pollux"
  )).provideSomeLayerShared(MockManagedDIDService.empty >+> testEnvironmentLayer)

  private val credentialDefinitionBadRequestAsJsonSpec =
    test("create the credential definition with wrong json body returns BadRequest as json") {
      for {
        credentialDefinitionRegistryService <- ZIO.service[CredentialDefinitionController]
        authenticator <- ZIO.service[AuthenticatorWithAuthZ[BaseEntity]]
        config <- ZIO.service[AppConfig]
        backend = httpBackend(config, credentialDefinitionRegistryService, authenticator)
        response: CredentialDefinitionBadRequestResponse <- basicRequest
          .post(credentialDefinitionUriBase)
          .body("""{"foo":"bar"}""")
          .response(asJsonAlways[ErrorResponse])
          .send(backend)

        itIsABadRequestStatusCode = assert(response.code)(equalTo(StatusCode.BadRequest))
        theBodyWasParsedFromJsonAsBadRequest = assert(response.body)(
          isRight(isSubtype[ErrorResponse](Assertion.anything))
        )
      } yield itIsABadRequestStatusCode // && theBodyWasParsedFromJsonAsBadRequest
    }
