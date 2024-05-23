package steps.common

import interactions.Get
import io.cucumber.java.ParameterType
import io.cucumber.java.en.Given
import io.iohk.atala.automation.extensions.get
import io.iohk.atala.automation.serenity.ensure.Ensure
import net.serenitybdd.rest.SerenityRest
import net.serenitybdd.screenplay.Actor
import net.serenitybdd.screenplay.actors.OnStage
import org.apache.http.HttpStatus
import org.hyperledger.identus.client.models.*
import steps.connection.ConnectionSteps
import steps.credentials.IssueCredentialsSteps
import steps.did.PublishDidSteps
import java.lang.IllegalArgumentException

class CommonSteps {
    @ParameterType(".*")
    fun actor(actorName: String): Actor {
        return OnStage.theActorCalled(actorName)
    }

    @ParameterType(".*")
    fun curve(value: String): Curve {
        return Curve.decode(value) ?: throw IllegalArgumentException("$value is not a valid Curve value")
    }

    @ParameterType(".*")
    fun purpose(value: String): Purpose {
        return Purpose.decode(value) ?: throw IllegalArgumentException("$value is not a valid Purpose value")
    }

    @Given("{actor} has an issued credential from {actor}")
    fun holderHasIssuedCredentialFromIssuer(holder: Actor, issuer: Actor) {
        actorsHaveExistingConnection(issuer, holder)

        val publishDidSteps = PublishDidSteps()
        publishDidSteps.createsUnpublishedDid(holder)
        publishDidSteps.agentHasAPublishedDID(issuer)

        val issueSteps = IssueCredentialsSteps()
        issueSteps.issuerOffersACredential(issuer, holder, "short")
        issueSteps.holderReceivesCredentialOffer(holder)
        issueSteps.holderAcceptsCredentialOfferForJwt(holder)
        issueSteps.acmeIssuesTheCredential(issuer)
        issueSteps.bobHasTheCredentialIssued(holder)
    }

    @Given("{actor} and {actor} have an existing connection")
    fun actorsHaveExistingConnection(inviter: Actor, invitee: Actor) {
        inviter.attemptsTo(
            Get.resource("/connections"),
        )
        inviter.attemptsTo(
            Ensure.thatTheLastResponse().statusCode().isEqualTo(HttpStatus.SC_OK),
        )
        val inviterConnection = SerenityRest.lastResponse().get<ConnectionsPage>().contents!!.firstOrNull {
            it.label == "Connection with ${invitee.name}" && it.state == Connection.State.CONNECTION_RESPONSE_SENT
        }

        var inviteeConnection: Connection? = null
        if (inviterConnection != null) {
            invitee.attemptsTo(
                Get.resource("/connections"),
            )
            invitee.attemptsTo(
                Ensure.thatTheLastResponse().statusCode().isEqualTo(HttpStatus.SC_OK),
            )
            inviteeConnection = SerenityRest.lastResponse().get<ConnectionsPage>().contents!!.firstOrNull {
                it.theirDid == inviterConnection.myDid && it.state == Connection.State.CONNECTION_RESPONSE_RECEIVED
            }
        }

        if (inviterConnection != null && inviteeConnection != null) {
            inviter.remember("connection-with-${invitee.name}", inviterConnection)
            invitee.remember("connection-with-${inviter.name}", inviteeConnection)
        } else {
            val connectionSteps = ConnectionSteps()
            connectionSteps.inviterGeneratesAConnectionInvitation(inviter, invitee)
            connectionSteps.inviteeSendsAConnectionRequestToInviter(invitee, inviter)
            connectionSteps.inviterReceivesTheConnectionRequest(inviter)
            connectionSteps.inviteeReceivesTheConnectionResponse(invitee)
            connectionSteps.inviterAndInviteeHaveAConnection(inviter, invitee)
        }
    }
}
