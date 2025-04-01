package steps.verification

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.Curve
import common.*
import common.errors.JwtCredentialError
import interactions.Post
import interactions.body
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.*
import io.iohk.atala.automation.extensions.getList
import io.iohk.atala.automation.serenity.ensure.Ensure
import models.JwtCredential
import net.serenitybdd.rest.SerenityRest
import net.serenitybdd.screenplay.Actor
import org.apache.http.HttpStatus.SC_BAD_REQUEST
import org.apache.http.HttpStatus.SC_OK
import org.hyperledger.identus.client.models.*
import org.hyperledger.identus.client.models.VcVerification.*
import java.time.OffsetDateTime

typealias Verification = ParameterizableVcVerification

class VcVerificationSteps {

    @Given("{actor} uses that JWT VC issued from {actor} for Verification API")
    fun holderUsesThatJWTVCForVerificationAPI(holder: Actor, issuer: Actor) {
        val issuedCredential = holder.recall<IssueCredentialRecord>("issuedCredential")
        val jwt = JwtCredential.parseBase64(issuedCredential.credential!!).serialize()
        val issuerDid = issuer.recall<String>("shortFormDid")
        holder.remember("jwt", jwt)
        holder.remember("issuerDid", issuerDid)
    }

    @Given("{actor} has a JWT VC for Verification API")
    fun holderHasAJWTVCForVerificationAPI(holder: Actor) {
        val jwtCredential = VerifiableJwt.jwtVCv1()
        val jwt = jwtCredential.sign(JWSAlgorithm.ES256K, Curve.SECP256K1)
        holder.remember("jwt", jwt)
        holder.remember("issuerDid", "did:prism:issuer")
    }

    @Given("{actor} has a Verifiable Schema for Verification API")
    fun holderHasAVerifiableSchemaForVerificationAPI(holder: Actor) {
        val jwtCredential = VerifiableJwt.schemaVCv1()
        val jwt = jwtCredential.sign(JWSAlgorithm.ES384, Curve.SECP256K1)
        holder.remember("jwt", jwt)
        holder.remember("issuerDid", "did:prism:issuer")
    }

    @Given("{actor} has a '{}' problem in the Verifiable Credential")
    fun holderHasProblemInTheVerifiableCredential(holder: Actor, problem: JwtCredentialError) {
        val jwt = problem.jwt()
        holder.remember("jwt", jwt)
        holder.remember("issuerDid", "did:prism:issuer")
    }

    @When("{actor} sends the JWT Credential to {actor} Verification API")
    fun holderSendsJwtCredentialToVerificationAPI(holder: Actor, verifier: Actor, dataTable: DataTable) {
        // add type to datatable
        val checks = dataTable.asMap(VcVerification::class.java, Boolean::class.java)
        val jwt: String = holder.recall("jwt")
        val issuerDid: String = holder.recall("issuerDid")
        verifyJwt(verifier, jwt, issuerDid, checks)
        holder.remember("checks", checks)
    }

    @Then("{actor} should see that verification has failed with '{}' problem")
    fun holderShouldSeeThatVerificationHasFailedWithProblem(holder: Actor, problem: JwtCredentialError) {
    }

    @Then("{actor} should see that all checks have passed")
    fun holderShouldSeeThatVerificationHasPassed(holder: Actor) {
        val jwt: String = holder.recall("jwt")
        analyzeResponse(holder, jwt)
    }

    @Then("{actor} should see the check has failed")
    fun holderShouldSeeTheCheckHasFailed(holder: Actor) {
        holder.attemptsTo(
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_BAD_REQUEST),
        )
    }

    private fun verifyJwt(
        verifier: Actor,
        jwt: String,
        issuerDid: String,
        verifications: Map<VcVerification, Boolean>,
    ) {
        val now = OffsetDateTime.now()

        // creates the checks based on the data table from feature file
        val checks = verifications.map { (key, value) ->
            when (key) {
                ALGORITHM_VERIFICATION -> Verification(key) to value
                AUDIENCE_CHECK -> Verification(key, DidParameter(did = "did:prism:verifier")) to value
                COMPLIANCE_WITH_STANDARDS -> Verification(key) to value
                EXPIRATION_CHECK -> Verification(key, DateTimeParameter(dateTime = now.minusDays(5))) to value
                INTEGRITY_OF_CLAIMS -> Verification(key) to value
                ISSUER_IDENTIFICATION -> Verification(key, DidParameter(did = issuerDid)) to value
                NOT_BEFORE_CHECK -> Verification(key, DateTimeParameter(dateTime = now.plusDays(5))) to value
                REVOCATION_CHECK -> Verification(key) to value
                SCHEMA_CHECK -> Verification(key) to value
                SEMANTIC_CHECK_OF_CLAIMS -> Verification(key) to value
                SIGNATURE_VERIFICATION -> Verification(key) to value
                SUBJECT_VERIFICATION -> Verification(key, DidParameter(did = "did:prism:something")) to value
            }
        }.toMap()

        val vcVerificationRequest = VcVerificationRequest(jwt, checks.keys.toList())
        val payload = listOf(vcVerificationRequest)

        verifier.attemptsTo(
            Post.to("/verification/credential").body(payload),
        )
    }

    private fun analyzeResponse(holder: Actor, jwt: String) {
        val checks = holder.recall<Map<VcVerification, Boolean>>("checks")
        val actual = SerenityRest.lastResponse().getList<VcVerificationResponse>()

        holder.attemptsTo(
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_OK),
            Ensure.that(actual[0].credential).isEqualTo(jwt),
        )

        // check each verification result from the requested checks
        actual[0].result!!.forEach {
            val expected = checks.getOrElse(it.verification) {
                throw RuntimeException("Couldn't find ${it.verification} in verification request.")
            }

            holder.attemptsTo(
                Ensure.that(it.success).isEqualTo(expected)
                    .withReportedError("Expected [${it.verification}] to be [$expected] but got [${it.success}]"),
            )
        }
    }
}
