package org.hyperledger.identus.did.core.model.did

final case class Service(
    id: String,
    `type`: ServiceType,
    serviceEndpoint: ServiceEndpoint
) {

  def normalizeServiceEndpoint(): Service = copy(serviceEndpoint = serviceEndpoint.normalize())

}
