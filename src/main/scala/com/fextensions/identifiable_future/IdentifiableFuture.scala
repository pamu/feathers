package com.fextensions.identifiable_future

import scala.concurrent.Future


sealed trait IdentifiableFuture[+I, +A] {
  def id: I
  def future: Future[A]
}

case class FutureWithId[+I, +A](override val id: I, override val future: Future[A]) extends IdentifiableFuture[I, A]

object IdentifiableFuture {

  implicit class IdentifiableFutureListImplicits[+I, +A](futures: List[IdentifiableFuture[I, A]]) {

  }

}

