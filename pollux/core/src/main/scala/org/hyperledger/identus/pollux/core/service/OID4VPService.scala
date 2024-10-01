package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.pollux.prex.*
import org.hyperledger.identus.pollux.vc.jwt.JWT as PolluxJWT
import zio.*
import zio.json.ast.Json

import scala.language.implicitConversions

trait OIDC4VPService {
  def verifyJwt(ps: PresentationSubmission, vp: String): UIO[Unit]
}

class OID4VPServiceImpl(formatVerification: ClaimFormatVerification) extends OIDC4VPService {

  // TODO: use presentation_definition from the session object
  private val pd = PresentationDefinition(
    id = "3e216a58-2118-45ea-8db0-8798d01bb252",
    input_descriptors = Seq(
      InputDescriptor(
        id = "university_degree",
        constraints = Constraints(
          fields = Some(
            Seq(
              Field(
                path = Seq("$.vc.issuer"),
                filter = Some(
                  Json.Obj(
                    "type" -> Json.Str("string"),
                    "const" -> Json.Str("did:prism:ffd7cf5a0b73c82e1f16b41e1afcd8c9b0b87ca4f22c11328eacf546b611b1fc"),
                  )
                )
              ),
              Field(
                path = Seq("$.vc.credentialSubject.firstName"),
                filter = Some(Json.Obj("type" -> Json.Str("string")))
              ),
              Field(
                path = Seq("$.vc.credentialSubject.degree"),
                filter = Some(Json.Obj("type" -> Json.Str("string")))
              ),
              Field(
                path = Seq("$.vc.credentialSubject.grade"),
                filter = Some(Json.Obj("type" -> Json.Str("number")))
              ),
            )
          )
        )
      )
    )
  )

  // TODO: improve error handling
  override def verifyJwt(ps: PresentationSubmission, vp: String): UIO[Unit] = {
    for {
      _ <- PresentationSubmissionVerification
        .verify(pd, ps, Json.Str(vp))(formatVerification)
        .orDieWith(e => Exception(e.toString()))
    } yield ()
  }
}

object OID4VPServiceImpl {
  val layer: ULayer[OIDC4VPService] = ZLayer.fromZIO {
    val validateJwtVp = (jwt: PolluxJWT) => ZIO.dieMessage("not implemented")
    val validateJwtVc = (jwt: PolluxJWT) => ZIO.dieMessage("not implemented")
    for {
      // TODO: use actual verification
      cfv <- ZIO.succeed(
        ClaimFormatVerification(jwtVp = validateJwtVp, jwtVc = validateJwtVc)
      )
    } yield OID4VPServiceImpl(cfv)
  }
}
