package org.hyperledger.identus.pollux.credentialdefinition.controller

import org.hyperledger.identus.api.http.*
import org.hyperledger.identus.api.http.model.{Order, Pagination}
import org.hyperledger.identus.pollux.credentialdefinition.http.{
  CredentialDefinitionDidUrlResponsePage,
  CredentialDefinitionInput,
  CredentialDefinitionResponse,
  CredentialDefinitionResponsePage,
  FilterInput
}
import org.hyperledger.identus.pollux.PrismEnvelopeResponse
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*

import java.util.UUID
import scala.language.implicitConversions

trait CredentialDefinitionController {
  def createCredentialDefinition(in: CredentialDefinitionInput)(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialDefinitionResponse]

  def createCredentialDefinitionDidUrl(in: CredentialDefinitionInput)(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialDefinitionResponse]

  def getCredentialDefinitionByGuid(guid: UUID)(implicit
      rc: RequestContext
  ): IO[ErrorResponse, CredentialDefinitionResponse]

  def getCredentialDefinitionByGuidDidUrl(baseUrlServiceName: String, guid: UUID)(implicit
      rc: RequestContext
  ): IO[ErrorResponse, PrismEnvelopeResponse]

  def getCredentialDefinitionInnerDefinitionByGuid(id: UUID)(implicit
      rc: RequestContext
  ): IO[ErrorResponse, zio.json.ast.Json]

  def getCredentialDefinitionInnerDefinitionByGuidDidUrl(baseUrlServiceName: String, guid: UUID)(implicit
      rc: RequestContext
  ): IO[ErrorResponse, PrismEnvelopeResponse]

  def lookupCredentialDefinitions(
      filter: FilterInput,
      pagination: Pagination,
      order: Option[Order]
  )(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialDefinitionResponsePage]

  def lookupCredentialDefinitionsDidUrl(
      baseUrlServiceName: String,
      filter: FilterInput,
      pagination: Pagination,
      order: Option[Order]
  )(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialDefinitionDidUrlResponsePage]

}
