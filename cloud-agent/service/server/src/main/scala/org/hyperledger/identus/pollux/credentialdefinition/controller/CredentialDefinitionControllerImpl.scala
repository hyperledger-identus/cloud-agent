package org.hyperledger.identus.pollux.credentialdefinition.controller

import cats.implicits.*
import org.hyperledger.identus.agent.walletapi.model.{ManagedDIDState, PublicationState}
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.api.http.*
import org.hyperledger.identus.api.http.model.{CollectionStats, Order, Pagination}
import org.hyperledger.identus.castor.core.model.did.{LongFormPrismDID, PrismDID}
import org.hyperledger.identus.pollux.{credentialdefinition, PrismEnvelopeResponse}
import org.hyperledger.identus.pollux.core.model.schema.CredentialDefinition.FilteredEntries
import org.hyperledger.identus.pollux.core.model.ResourceResolutionMethod
import org.hyperledger.identus.pollux.core.service.CredentialDefinitionService
import org.hyperledger.identus.pollux.credentialdefinition.http.{
  CredentialDefinitionDidUrlResponse,
  CredentialDefinitionDidUrlResponsePage,
  CredentialDefinitionInnerDefinitionDidUrlResponse,
  CredentialDefinitionInput,
  CredentialDefinitionResponse,
  CredentialDefinitionResponsePage,
  FilterInput
}
import org.hyperledger.identus.pollux.credentialdefinition.http.CredentialDefinitionInput.toDomain
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*
import zio.json.ast.Json

import java.util.UUID
import scala.language.implicitConversions

class CredentialDefinitionControllerImpl(service: CredentialDefinitionService, managedDIDService: ManagedDIDService)
    extends CredentialDefinitionController {
  override def createCredentialDefinition(
      in: CredentialDefinitionInput
  )(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialDefinitionResponse] = {
    for {
      _ <- validatePrismDID(in.author)
      result <- service
        .create(toDomain(in))
        .map(cs => CredentialDefinitionResponse.fromDomain(cs).withBaseUri(rc.request.uri))
    } yield result
  }

  private def couldNotParseCredDefResponse(e: String) = ErrorResponse
    .internalServerError(detail = Some(s"Error occurred while parsing the credential definition response: $e"))

  override def createCredentialDefinitionDidUrl(
      in: CredentialDefinitionInput
  )(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialDefinitionResponse] = {
    for {
      _ <- validatePrismDID(in.author)
      result <- service
        .create(toDomain(in), ResourceResolutionMethod.did)
        .map(cs => CredentialDefinitionResponse.fromDomain(cs).withBaseUri(rc.request.uri))
    } yield result
  }

  override def getCredentialDefinitionByGuid(
      guid: UUID
  )(implicit rc: RequestContext): IO[ErrorResponse, CredentialDefinitionResponse] = {
    service
      .getByGUID(guid)
      .map(
        CredentialDefinitionResponse
          .fromDomain(_)
          .withSelf(rc.request.uri.toString)
      )
  }

  override def getCredentialDefinitionByGuidDidUrl(
      baseUrlServiceName: String,
      guid: UUID
  )(implicit rc: RequestContext): IO[ErrorResponse, PrismEnvelopeResponse] = {

    val res = for {
      cd <- service.getByGUID(guid, ResourceResolutionMethod.did)
      response <- ZIO
        .fromEither(CredentialDefinitionDidUrlResponse.asPrismEnvelopeResponse(cd, baseUrlServiceName))
        .mapError(couldNotParseCredDefResponse)

    } yield response

    res
  }

  override def getCredentialDefinitionInnerDefinitionByGuid(id: UUID)(implicit
      rc: RequestContext
  ): IO[ErrorResponse, Json] = {
    service
      .getByGUID(id)
      .map(CredentialDefinitionResponse.fromDomain(_).definition)
  }

  override def getCredentialDefinitionInnerDefinitionByGuidDidUrl(baseUrlServiceName: String, guid: UUID)(implicit
      rc: RequestContext
  ): IO[ErrorResponse, PrismEnvelopeResponse] = {
    val res = for {
      cd <- service.getByGUID(guid, ResourceResolutionMethod.did)
      authorDid <- ZIO
        .fromEither(PrismDID.fromString(cd.author))
        .mapError(_ => ErrorResponse.internalServerError(detail = Some("Invalid credential definition author DID")))
      response <- ZIO
        .fromEither(
          CredentialDefinitionInnerDefinitionDidUrlResponse
            .asPrismEnvelopeResponse(cd.definition, authorDid, cd.guid, baseUrlServiceName)
        )
        .mapError(couldNotParseCredDefResponse)

    } yield response

    res
  }

  override def lookupCredentialDefinitions(
      filter: FilterInput,
      pagination: Pagination,
      order: Option[Order]
  )(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialDefinitionResponsePage] = {
    for {
      filteredEntries: FilteredEntries <- service.lookup(
        filter.toDomain(),
        pagination.offset,
        pagination.limit
      )
      entries = filteredEntries.entries
        .map(CredentialDefinitionResponse.fromDomain(_).withBaseUri(rc.request.uri))
        .toList
      page = CredentialDefinitionResponsePage(entries)
      stats = CollectionStats(filteredEntries.totalCount, filteredEntries.count)
    } yield CredentialDefinitionControllerLogic(rc, pagination, stats).result(page)
  }

  override def lookupCredentialDefinitionsDidUrl(
      baseUrlServiceName: String,
      filter: FilterInput,
      pagination: Pagination,
      order: Option[Order]
  )(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialDefinitionDidUrlResponsePage] = {
    for {
      filteredEntries: FilteredEntries <- service.lookup(
        filter.toDomain(ResourceResolutionMethod.did),
        pagination.offset,
        pagination.limit
      )

      entriesZio = filteredEntries.entries
        .traverse(cd => CredentialDefinitionDidUrlResponse.asPrismEnvelopeResponse(cd, baseUrlServiceName))

      entries <- ZIO
        .fromEither(entriesZio)
        .mapError(couldNotParseCredDefResponse)

      page = CredentialDefinitionDidUrlResponsePage(entries)
      stats = CollectionStats(filteredEntries.totalCount, filteredEntries.count)
    } yield CredentialDefinitionControllerLogic(rc, pagination, stats).resultDidUrl(page)
  }

  private def validatePrismDID(author: String) =
    for {
      authorDID <- ZIO
        .fromEither(PrismDID.fromString(author))
        .mapError(ex => ErrorResponse.badRequest(detail = Some(s"Unable to parse Prism DID from '${author}' due: $ex")))
      longFormPrismDID <- getLongForm(authorDID, true)
    } yield longFormPrismDID

  private def getLongForm(
      did: PrismDID,
      allowUnpublishedIssuingDID: Boolean = false
  ): ZIO[WalletAccessContext, ErrorResponse, LongFormPrismDID] = {
    for {
      didState <- managedDIDService
        .getManagedDIDState(did.asCanonical)
        .mapError(e =>
          ErrorResponse.internalServerError(detail =
            Some(s"Error occurred while getting DID from wallet: ${e.toString}")
          )
        )
        .someOrFail(ErrorResponse.notFound(detail = Some(s"Issuer DID does not exist in the wallet: $did")))
        .flatMap {
          case s @ ManagedDIDState(_, _, _: PublicationState.Published) => ZIO.succeed(s)
          case s                                                        =>
            ZIO.cond(
              allowUnpublishedIssuingDID,
              s,
              ErrorResponse.badRequest(detail = Some(s"Issuer DID must be published: $did"))
            )
        }
      longFormPrismDID = PrismDID.buildLongFormFromOperation(didState.createOperation)
    } yield longFormPrismDID
  }
}

object CredentialDefinitionControllerImpl {
  val layer: URLayer[CredentialDefinitionService & ManagedDIDService, CredentialDefinitionController] =
    ZLayer.fromFunction(CredentialDefinitionControllerImpl(_, _))
}
