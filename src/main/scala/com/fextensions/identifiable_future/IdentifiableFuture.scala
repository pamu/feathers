package com.fextensions.identifiable_future

import scala.concurrent.{Future, Promise}
import scala.util.{Success, Try}

sealed trait IdentifiableFuture[I, +A] {
  self =>

  type pair = (I, A)

  def id: I

  def future: Future[A]

  def map[B](f: A => B): IdentifiableFuture[I, B] = new IdentifiableFuture[I, B] {
    override def id: I = self.id

    override def future: Future[B] = self.future.map(f)
  }

  def flatMap[B](f: A => IdentifiableFuture[I, B]) = new IdentifiableFuture[I, B] {
    override def id: I = self.id
    override def future: Future[B] = self.future.flatMap(f(_).future)
  }

  def recover[B >: A](f: PartialFunction[Throwable, B]) = new IdentifiableFuture[I, B] {
    override def id: I = self.id

    override def future: Future[B] = self.future.recover(f)
  }

  def recoverWith[B >: A](f: PartialFunction[Throwable, IdentifiableFuture[I, B]]) = new IdentifiableFuture[I, B] {
    override def id: I = self.id

    override def future: Future[B] = self.future.recoverWith { case th => f(th).future }
  }

  def tryMap[B](f: Try[A] => B): IdentifiableFuture[I, B] = {
    val promise = Promise[B]()
    self.future.onComplete(value => promise.tryComplete(Success(f(value))))
    new IdentifiableFuture[I, B] {
      override def id: I = self.id
      override def future: Future[B] = promise.future
    }
  }

  def tryFlatMap[B](f: Try[A] => IdentifiableFuture[I, B]): IdentifiableFuture[I, B] = {
    val promise = Promise[B]()
    self.future.onComplete(value => promise.tryCompleteWith(f(value).future))
    new IdentifiableFuture[I, B] {
      override def id: I = self.id
      override def future: Future[B] = promise.future
    }
  }
}

case class FutureWithId[+I, +A](override val id: I)(override val future: Future[A]) extends IdentifiableFuture[I, A]

object IdentifiableFuture {
  
  def apply[I, A](id: I, future: Future[A]): IdentifiableFuture[I, A] = new IdentifiableFuture[I, A]() {
    override def id: I = id
    override def future: Future[A] = future
  }

  def successful[I, A](id: I, value: A) = new IdentifiableFuture[I, A] {
    override def id: I = id

    override def future: Future[A] = Future.successful(value)
  }

  implicit class IdentifiableFutureListImplicits[+I, +A](futures: List[IdentifiableFuture[I, A]]) {

  }

}

