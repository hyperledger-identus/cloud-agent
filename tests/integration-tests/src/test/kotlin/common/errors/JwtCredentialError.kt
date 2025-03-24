package common.errors

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.Curve
import common.VerifiableJwt
import models.JwtCredential
import org.hyperledger.identus.client.models.VcVerification
import java.time.OffsetDateTime

enum class JwtCredentialError {
    ALGORITHM_VERIFICATION {
        override fun jwt(): String {
            val jwt = VerifiableJwt.jwtVCv1()
            return jwt.sign(JWSAlgorithm.HS256, null)
        }
        override val verification = VcVerification.ALGORITHM_VERIFICATION
    },
    AUDIENCE_CHECK {
        override fun jwt(): String {
            val jwt = VerifiableJwt.jwtVCv1()
            jwt.audience("did:wrong")
            return jwt.sign(defaultAlgorithm, defaultCurve)
        }
        override val verification = VcVerification.AUDIENCE_CHECK
    },
    COMPLIANCE_WITH_STANDARDS {
        override fun jwt(): String {
            TODO("Not supported yet")
        }

        override val verification = VcVerification.COMPLIANCE_WITH_STANDARDS
    },
    EXPIRATION_CHECK {
        override fun jwt(): String {
            val jwt = VerifiableJwt.jwtVCv1()
            jwt.expirationTime(OffsetDateTime.now().plusYears(10))
            return jwt.sign(defaultAlgorithm, defaultCurve)
        }

        override val verification = VcVerification.EXPIRATION_CHECK
    },
    INTEGRITY_OF_CLAIMS {
        override fun jwt(): String {
            TODO("Not supported yet")
        }
        override val verification = VcVerification.INTEGRITY_OF_CLAIMS
    },
    ISSUER_IDENTIFICATION {
        override fun jwt(): String {
            val jwt = VerifiableJwt.jwtVCv1()
            jwt.issuer("did:wrong")
            return jwt.sign(defaultAlgorithm, defaultCurve)
        }
        override val verification = VcVerification.ISSUER_IDENTIFICATION
    },
    NOT_BEFORE_CHECK {
        override fun jwt(): String {
            val jwt = VerifiableJwt.jwtVCv1()
            jwt.notBefore(OffsetDateTime.now().minusYears(10))
            return jwt.sign(defaultAlgorithm, defaultCurve)
        }
        override val verification = VcVerification.NOT_BEFORE_CHECK
    },
    REVOCATION_CHECK {
        override fun jwt(): String {
            TODO("Not supported yet")
        }
        override val verification = VcVerification.REVOCATION_CHECK
    },
    SCHEMA_CHECK {
        override fun jwt(): String {
            TODO("Not supported yet")
        }
        override val verification = VcVerification.SCHEMA_CHECK
    },
    SEMANTIC_CHECK_OF_CLAIMS {
        override fun jwt(): String {
            val jwt = VerifiableJwt.jwtVCv1()
            val jwtCredential = JwtCredential()
            val claims = mutableMapOf<String, Any>()
            claims.putAll(jwt.claimSetBuilder.claims)
            claims.remove("iss")
            jwtCredential.claims(claims)
            return jwt.sign(defaultAlgorithm, defaultCurve)
        }
        override val verification = VcVerification.SEMANTIC_CHECK_OF_CLAIMS
    },
    SIGNATURE_VERIFICATION {
        override fun jwt(): String {
            val jwt = VerifiableJwt.jwtVCv1()
            return jwt.sign(defaultAlgorithm, defaultCurve)
        }
        override val verification = VcVerification.SIGNATURE_VERIFICATION
    },
    SUBJECT_VERIFICATION {
        override fun jwt(): String {
            TODO("Not yet implemented")
        }
        override val verification = VcVerification.SUBJECT_VERIFICATION
    }, ;

    companion object {
        init {
            // forcefully check if JwtCredentialProblems has all VcVerification
            // cases since it's not possible to inherit final class
            VcVerification.entries.forEach {
                try {
                    JwtCredentialError.valueOf(it.name)
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("JwtCredentialProblem does not contain the new ${it.name} VcVerification case")
                }
            }
        }
    }

    protected val defaultAlgorithm = JWSAlgorithm.ES256K
    protected val defaultCurve = Curve.SECP256K1

    abstract fun jwt(): String
    abstract val verification: VcVerification
}
