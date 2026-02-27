package org.hyperledger.identus.castor.core.util

import org.hyperledger.identus.castor.core.model.did.*
import org.hyperledger.identus.castor.core.model.did.ServiceEndpoint.{UriOrJsonEndpoint, UriValue}
import org.hyperledger.identus.shared.crypto.Apollo
import org.hyperledger.identus.shared.models.{Base64UrlString, KeyId}
import zio.*
import zio.json.ast.Json
import zio.test.Gen

import scala.language.implicitConversions

object GenUtils {

  given Conversion[String, ServiceType.Name] = ServiceType.Name.fromStringUnsafe

  val uriFragment: Gen[Any, String] = Gen.stringBounded(1, 20)(Gen.asciiChar).filter(UriUtils.isValidUriFragment)

  val uri: Gen[Any, String] =
    for {
      scheme <- Gen.fromIterable(Seq("http", "https", "ftp", "ws", "wss", "file", "imap", "ssh"))
      host <- Gen.alphaNumericStringBounded(1, 10)
      path <- Gen.listOfBounded(0, 5)(Gen.alphaNumericStringBounded(1, 10)).map(_.mkString("/"))
      uri <- Gen.const(s"$scheme://$host/$path").map(UriUtils.normalizeUri).collect { case Some(uri) => uri }
    } yield uri

  // TODO: generate all key types
  val publicKeyData: Gen[Any, PublicKeyData] =
    for {
      curve <- Gen.const(EllipticCurve.SECP256K1)
      pk <- Gen.fromZIO(ZIO.succeed(Apollo.default.secp256k1.generateKeyPair.publicKey))
      x = Base64UrlString.fromByteArray(pk.getECPoint.x)
      y = Base64UrlString.fromByteArray(pk.getECPoint.y)
      uncompressedKey = PublicKeyData.ECKeyData(curve, x, y)
      compressedKey = PublicKeyData.ECCompressedKeyData(curve, Base64UrlString.fromByteArray(pk.getEncodedCompressed))
      generated <- Gen.fromIterable(Seq(uncompressedKey, compressedKey))
    } yield generated

  val publicKey: Gen[Any, PublicKey] =
    for {
      id <- uriFragment
      purpose <- Gen.fromIterable(VerificationRelationship.values)
      keyData <- publicKeyData
    } yield PublicKey(KeyId(id), purpose, keyData)

  val internalPublicKey: Gen[Any, InternalPublicKey] =
    for {
      id <- uriFragment
      purpose <- Gen.fromIterable(InternalKeyPurpose.values)
      keyData <- publicKeyData
    } yield InternalPublicKey(KeyId(id), purpose, keyData)

  val service: Gen[Any, Service] =
    for {
      id <- uriFragment
      serviceType <- Gen.oneOf(
        Gen.const(ServiceType.Single("LinkedDomains")),
        Gen
          .int(0, 1)
          .map(n => Seq[ServiceType.Name]("CredentialRepository").take(n))
          .map(tail => ServiceType.Multiple("LinkedDomains", tail))
      )
      sampleUri = "https://example.com"
      uriEndpointGen = Gen.const(UriOrJsonEndpoint.Uri(UriValue.fromString(sampleUri).toOption.get))
      jsonEndpointGen = Gen.const(UriOrJsonEndpoint.Json(Json.Obj("uri" -> Json.Str(sampleUri)).asObject.get))
      endpoints <- Gen.oneOf[Any, ServiceEndpoint](
        uriEndpointGen.map(ServiceEndpoint.Single(_)),
        jsonEndpointGen.map(ServiceEndpoint.Single(_)),
        Gen
          .listOfBounded(1, 3)(Gen.oneOf[Any, UriOrJsonEndpoint](uriEndpointGen, jsonEndpointGen))
          .map(xs => ServiceEndpoint.Multiple(xs.head, xs.tail))
      )
    } yield Service(id, serviceType, endpoints).normalizeServiceEndpoint()

  val createOperation: Gen[Any, PrismDIDOperation.Create] = {
    for {
      masterKey <- internalPublicKey.map(_.copy(purpose = InternalKeyPurpose.Master))
      publicKeys <- Gen.listOfBounded(0, 5)(publicKey)
      keys: List[InternalPublicKey | PublicKey] = masterKey :: publicKeys
      services <- Gen.listOfBounded(0, 5)(service)
      contexts <- Gen.listOfBounded(0, 5)(uri)
    } yield PrismDIDOperation.Create(keys, services, contexts)
  }

  val longFormPrismDIDGen: Gen[Any, LongFormPrismDID] = createOperation.map(PrismDID.buildLongFormFromOperation)
  val canonicalPrismDIDGen: Gen[Any, CanonicalPrismDID] = createOperation.map(_.did)
  val rawPrismDIDGen: Gen[Any, String] =
    Gen.oneOf(longFormPrismDIDGen.map(_.toString), canonicalPrismDIDGen.map(_.toString))

  val didData: Gen[Any, DIDData] = {
    for {
      op <- createOperation
    } yield DIDData(
      id = op.did,
      publicKeys = op.publicKeys.collect { case pk: PublicKey => pk },
      services = op.services,
      internalKeys = op.publicKeys.collect { case pk: InternalPublicKey => pk },
      context = op.context
    )
  }

  val pathGen: Gen[Any, String] = for {
    numSegments <- Gen.int(1, 5)
    segments <- Gen.listOfN(numSegments)(Gen.alphaNumericStringBounded(1, 10))
  } yield segments.mkString("/", "/", "")

  val queryGen: Gen[Any, String] = for {
    numParams <- Gen.int(1, 5)
    params <- Gen
      .listOfN(numParams)(
        for {
          key <- Gen.alphaNumericStringBounded(1, 10)
          value <- Gen.alphaNumericStringBounded(1, 10)
        } yield key -> value
      )
      .map(_.toMap.map { case (k, v) => s"$k=$v" })

  } yield params.mkString("?", "&", "")

  val fragmentGen: Gen[Any, String] = Gen.alphaNumericStringBounded(1, 10).map("#" + _)

  val prismDIDUrlGen: Gen[Any, String] = for {
    did <- rawPrismDIDGen
    path <- Gen.option(pathGen)
    query <- Gen.option(queryGen)
    fragment <- Gen.option(fragmentGen)
  } yield {
    val pathPart = path.getOrElse("")
    val queryPart = query.getOrElse("")
    val fragmentPart = fragment.getOrElse("")
    s"$did$pathPart$queryPart$fragmentPart"
  }

  def inputDIDUrlGen(input: Seq[String]): Gen[Any, String] = for {
    did <- Gen.fromIterable(input)
    path <- Gen.option(pathGen)
    query <- Gen.option(queryGen)
    fragment <- Gen.option(fragmentGen)
  } yield {
    val pathPart = path.getOrElse("")
    val queryPart = query.getOrElse("")
    val fragmentPart = fragment.getOrElse("")
    s"$did$pathPart$queryPart$fragmentPart"
  }

}
