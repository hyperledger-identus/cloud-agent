package org.hyperledger.identus.shared.utils

/** A lightweight DIY version of traverse to not rely on Cats because Scala3 + Cats implicits break the IDE compile
  * server making the development counterproductive.
  */
object Traverse {

  extension [A](ls: Seq[A]) {
    def traverse[E, B](f: A => Either[E, B]): Either[E, Seq[B]] =
      ls.foldRight[Either[E, List[B]]](Right(Nil))((i, acc) => acc.flatMap(ls => f(i).map(_ :: ls)))
  }

  extension [A, E](ls: Seq[Either[E, A]]) {
    def sequence: Either[E, Seq[A]] = ls.traverse(identity)
  }

}
