package org.hyperledger.identus.pollux.vc.jwt

import zio.{Trace, ZIO}
import zio.prelude.{Validation, ZValidation}

object ValidationUtils {
  final def foreach[R, E, W, VE, A, B](in: ZValidation[W, VE, A])(f: A => ZIO[R, E, B])(implicit
      trace: Trace
  ): ZIO[R, E, ZValidation[W, VE, B]] =
    in.fold(e => ZIO.succeed(Validation.failNonEmptyChunk(e)), f(_).map(Validation.succeed))

}
