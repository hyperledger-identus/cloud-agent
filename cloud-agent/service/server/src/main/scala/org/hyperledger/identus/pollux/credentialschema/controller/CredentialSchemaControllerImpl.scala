package org.hyperledger.identus.pollux.credentialschema.controller

import cats.implicits.*
import org.hyperledger.identus.agent.walletapi.model.{ManagedDIDState, PublicationState}
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.api.http.*
import org.hyperledger.identus.api.http.model.{CollectionStats, Order, Pagination}
import org.hyperledger.identus.castor.core.model.did.{LongFormPrismDID, PrismDID}
import org.hyperledger.identus.pollux.core.model
import org.hyperledger.identus.pollux.core.model.schema.CredentialSchema.FilteredEntries
import org.hyperledger.identus.pollux.core.model.ResourceResolutionMethod
import org.hyperledger.identus.pollux.core.service.CredentialSchemaService
import org.hyperledger.identus.pollux.credentialschema.http.{
  CredentialSchemaDidUrlResponse,
  CredentialSchemaDidUrlResponsePage,
  CredentialSchemaInnerDidUrlResponse,
  CredentialSchemaInput,
  CredentialSchemaResponse,
  CredentialSchemaResponsePage,
  FilterInput
}
import org.hyperledger.identus.pollux.credentialschema.http.CredentialSchemaInput.toDomain
import org.hyperledger.identus.pollux.PrismEnvelopeResponse
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*
import zio.json.*
import zio.json.ast.Json

import java.util.UUID
import scala.language.implicitConversions

class CredentialSchemaControllerImpl(service: CredentialSchemaService, managedDIDService: ManagedDIDService)
    extends CredentialSchemaController {

  private def parsingCredentialSchemaError(e: String) = ErrorResponse
    .internalServerError(detail = Some(s"Error occurred while parsing the credential schema response: $e"))

  override def createSchema(
      in: CredentialSchemaInput
  )(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialSchemaResponse] = {
    for {
      _ <- validatePrismDID(in.author)
      result <- service
        .create(toDomain(in))
        .map(cs => CredentialSchemaResponse.fromDomain(cs).withBaseUri(rc.request.uri))
    } yield result
  }

  def createSchemaDidUrl(baseUrlServiceName: String, in: CredentialSchemaInput)(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, PrismEnvelopeResponse] = {
    val res = for {
      validated <- validatePrismDID(in.author)
      result <- service.create(toDomain(in), ResourceResolutionMethod.did)
      response <- ZIO
        .fromEither(CredentialSchemaDidUrlResponse.asPrismEnvelopeResponse(result, baseUrlServiceName))
        .mapError(parsingCredentialSchemaError)

    } yield response

    res
  }

  override def updateSchema(id: UUID, in: CredentialSchemaInput)(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialSchemaResponse] = {
    for {
      _ <- validatePrismDID(in.author)
      result <- service
        .update(id, toDomain(in))
        .map(cs => CredentialSchemaResponse.fromDomain(cs).withBaseUri(rc.request.uri))
    } yield result
  }

  override def updateSchemaDidUrl(baseUrlServiceName: String, id: UUID, in: CredentialSchemaInput)(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, PrismEnvelopeResponse] = {
    val res = for {
      _ <- validatePrismDID(in.author)
      cs <- service
        .update(id, toDomain(in), ResourceResolutionMethod.did)
      result <- ZIO
        .fromEither(CredentialSchemaDidUrlResponse.asPrismEnvelopeResponse(cs, baseUrlServiceName))
        .mapError(parsingCredentialSchemaError)
    } yield result

    res
  }

  override def getSchemaByGuid(guid: UUID)(implicit
      rc: RequestContext
  ): IO[ErrorResponse, CredentialSchemaResponse] = {
    service
      .getByGUID(guid)
      .map(
        CredentialSchemaResponse
          .fromDomain(_)
          .withSelf(rc.request.uri.toString)
      )
  }

  override def getSchemaByGuidDidUrl(baseUrlServiceName: String, guid: UUID)(implicit
      rc: RequestContext
  ): IO[ErrorResponse, PrismEnvelopeResponse] = {
    val res: IO[ErrorResponse, PrismEnvelopeResponse] = for {
      cs <- service.getByGUID(guid, ResourceResolutionMethod.did)
      response <- ZIO
        .fromEither(CredentialSchemaDidUrlResponse.asPrismEnvelopeResponse(cs, baseUrlServiceName))
        .mapError(parsingCredentialSchemaError)
    } yield response

    res
  }

  override def getSchemaJsonByGuid(guid: UUID)(implicit
      rc: RequestContext
  ): IO[ErrorResponse, Json] = {
    service
      .getByGUID(guid)
      .map(
        _.schema
      )
  }

  override def getSchemaJsonByGuidDidUrl(baseUrlServiceName: String, id: UUID)(implicit
      rc: RequestContext
  ): IO[ErrorResponse, PrismEnvelopeResponse] = {
    val res = for {
      cs <- service.getByGUID(id, ResourceResolutionMethod.did)
      authorDid <- ZIO
        .fromEither(PrismDID.fromString(cs.author))
        .mapError(_ => ErrorResponse.internalServerError(detail = Some("Invalid schema author DID")))
      response <- ZIO
        .fromEither(
          CredentialSchemaInnerDidUrlResponse.asPrismEnvelopeResponse(cs.schema, authorDid, cs.id, baseUrlServiceName)
        )
        .mapError(parsingCredentialSchemaError)
    } yield response

    res
  }

  override def lookupSchemas(
      filter: FilterInput,
      pagination: Pagination,
      order: Option[Order]
  )(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialSchemaResponsePage] = {
    for {
      filteredEntries: FilteredEntries <- service.lookup(
        filter.toDomain(),
        pagination.offset,
        pagination.limit
      )
      entries = filteredEntries.entries
        .map(CredentialSchemaResponse.fromDomain(_).withBaseUri(rc.request.uri))
        .toList
      page = CredentialSchemaResponsePage(entries)
      stats = CollectionStats(filteredEntries.totalCount, filteredEntries.count)
    } yield CredentialSchemaControllerLogic(rc, pagination, stats).result(page)
  }

  override def lookupSchemasDidUrl(
      baseUrlServiceName: String,
      filter: FilterInput,
      pagination: Pagination,
      order: Option[Order],
  )(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialSchemaDidUrlResponsePage] = {
    for {
      filteredEntries: FilteredEntries <- service.lookup(
        filter.toDomain(ResourceResolutionMethod.did),
        pagination.offset,
        pagination.limit
      )
      entriesZio = filteredEntries.entries
        .traverse(cs => CredentialSchemaDidUrlResponse.asPrismEnvelopeResponse(cs, baseUrlServiceName))

      entries <- ZIO
        .fromEither(entriesZio)
        .mapError(e =>
          ErrorResponse.internalServerError(detail = Some(s"Error occurred while parsing a schema response: $e"))
        )

      page = CredentialSchemaDidUrlResponsePage(entries)
      stats = CollectionStats(filteredEntries.totalCount, filteredEntries.count)
    } yield CredentialSchemaControllerLogic(rc, pagination, stats).resultDidUrl(page)
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

object CredentialSchemaControllerImpl {
  val layer: URLayer[CredentialSchemaService & ManagedDIDService, CredentialSchemaController] =
    ZLayer.fromFunction(CredentialSchemaControllerImpl(_, _))
}
