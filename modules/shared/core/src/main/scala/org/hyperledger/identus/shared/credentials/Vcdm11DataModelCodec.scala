package org.hyperledger.identus.shared.credentials

import zio.*
import zio.json.*
import zio.json.ast.Json

object Vcdm11DataModelCodec extends DataModelCodec:

  override def modelType: DataModelType = DataModelType.VCDM_1_1

  override def encodeClaims(claims: Json, meta: Json): IO[Throwable, Json] =
    ZIO.attempt {
      val metaObj = meta.asObject.getOrElse(Json.Obj.empty.asObject.get)
      val issuer = metaObj.get("issuer").getOrElse(Json.Str(""))
      val issuanceDate = metaObj.get("issuanceDate").getOrElse(Json.Str(java.time.Instant.now().toString))

      val baseFields = Seq(
        "@context" -> Json.Arr(Json.Str("https://www.w3.org/2018/credentials/v1")),
        "type" -> Json.Arr(Json.Str("VerifiableCredential")),
        "issuer" -> issuer,
        "issuanceDate" -> issuanceDate,
        "credentialSubject" -> claims,
      )

      val reserved = Set("@context", "type", "issuer", "issuanceDate", "credentialSubject")
      val extraFields = metaObj.fields.collect {
        case (k, v) if !reserved.contains(k) => k -> v
      }

      Json.Obj(zio.Chunk.from(baseFields) ++ extraFields)
    }

  override def decodeClaims(raw: RawCredential): IO[Throwable, Json] =
    ZIO.attempt {
      val jsonStr = new String(raw.data, "UTF-8")
      val json = jsonStr.fromJson[Json].fold(err => throw new Exception(s"Invalid JSON: $err"), identity)
      json.asObject
        .flatMap(_.get("credentialSubject"))
        .getOrElse(throw new Exception("Missing credentialSubject"))
    }

  override def validateStructure(raw: RawCredential): IO[Throwable, Unit] =
    ZIO.attempt {
      val jsonStr = new String(raw.data, "UTF-8")
      val json = jsonStr.fromJson[Json].fold(err => throw new Exception(s"Invalid JSON: $err"), identity)
      val obj = json.asObject.getOrElse(throw new Exception("VC must be a JSON object"))
      if obj.get("@context").flatMap(_.asArray).isEmpty then throw new Exception("Missing @context")
      if obj.get("type").flatMap(_.asArray).isEmpty then throw new Exception("Missing type")
      if obj.get("credentialSubject").isEmpty then throw new Exception("Missing credentialSubject")
    }
