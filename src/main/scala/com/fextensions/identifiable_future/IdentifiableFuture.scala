package com.fextensions.identifiable_future

import scala.concurrent.Future


sealed trait IdentifiableFuture[I, +A] { self =>
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
}

case class FutureWithId[+I, +A](override val id: I, override val future: Future[A]) extends IdentifiableFuture[I, A]

object IdentifiableFuture {

  def successful[I, A](id: I, value: A) = new IdentifiableFuture[I, A] {
    override def id: I = id
    override def future: Future[A] = Future.successful(value)
  }

  implicit class IdentifiableFutureListImplicits[+I, +A](futures: List[IdentifiableFuture[I, A]]) {

  }

}

