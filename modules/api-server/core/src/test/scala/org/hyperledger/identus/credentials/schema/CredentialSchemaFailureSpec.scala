package org.hyperledger.identus.credentials.schema

import com.dimafeng.testcontainers.PostgreSQLContainer
import org.hyperledger.identus.server.config.AppConfig
import org.hyperledger.identus.wallet.model.BaseEntity
import org.hyperledger.identus.wallet.service.MockManagedDIDService
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.container.util.MigrationAspects.migrate
import org.hyperledger.identus.iam.authentication.AuthenticatorWithAuthZ
import org.hyperledger.identus.credentials.credentialschema.*
import org.hyperledger.identus.credentials.credentialschema.controller.CredentialSchemaController
import sttp.client3.{basicRequest, DeserializationException}
import sttp.client3.ziojson.*
import sttp.model.StatusCode
import zio.*
import zio.test.*
import zio.test.Assertion.*

object CredentialSchemaFailureSpec extends ZIOSpecDefault with CredentialSchemaTestTools:

  def spec = (schemaBadRequestAsJsonSpec @@ migrate(
    schema = "public",
    paths = "classpath:sql/pollux"
  )).provide(testEnvironmentLayer, MockManagedDIDService.empty)

  private val schemaBadRequestAsJsonSpec = suite("schema-registry BadRequest as json logic")(
    test("create the schema with wrong json body returns BadRequest as json") {
      for {
        controller <- ZIO.service[CredentialSchemaController]
        authenticator <- ZIO.service[AuthenticatorWithAuthZ[BaseEntity]]
        config <- ZIO.service[AppConfig]
        backend = httpBackend(config, controller, authenticator)
        response: SchemaBadRequestResponse <- basicRequest
          .post(credentialSchemaUriBase)
          .body("""{"foo":"bar"}""")
          .response(asJsonAlways[ErrorResponse])
          .send(backend)

        itIsABadRequestStatusCode = assert(response.code)(equalTo(StatusCode.BadRequest))
        theBodyWasParsedFromJsonAsBadRequest = assert(response.body)(
          isRight(isSubtype[ErrorResponse](Assertion.anything))
        )
      } yield itIsABadRequestStatusCode && theBodyWasParsedFromJsonAsBadRequest
    }
  )
