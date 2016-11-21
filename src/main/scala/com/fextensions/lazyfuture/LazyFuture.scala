/*
 * Copyright (c) 2016 Pamu Nagarjuna (http://pamu.github.io).
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.fextensions.lazyfuture

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import com.fextensions.All._

sealed trait LazyFuture[+A] {

  protected val lazyFuture:  () => Future[A]

  def run(): Future[A] = lazyFuture()

  def map[B](f: A => B)(implicit ec: ExecutionContext): LazyFuture[B] = {
    LazyFuture(lazyFuture().map(f))
  }

  def flatMap[B](f: A => LazyFuture[B])(implicit ec: ExecutionContext): LazyFuture[B] = {
    LazyFuture(lazyFuture().flatMap(f(_).run()))
  }

  def fallBackTo[B >: A](delayedFuture: LazyFuture[B])(implicit ex: ExecutionContext): LazyFuture[B] = {
    LazyFuture(delayedFuture.run().fallbackTo(delayedFuture.run()))
  }

  def foreach(f: A => Unit)(implicit ec: ExecutionContext): Unit = {
   lazyFuture().foreach(f)
  }

  def onComplete(f: Try[A] => Unit)(implicit ec: ExecutionContext): Unit = {
    lazyFuture().onComplete(f)
  }

  def recoverWith[B >: A](pf: PartialFunction[Throwable, LazyFuture[B]])(implicit ex: ExecutionContext): LazyFuture[B] = {
    LazyFuture(lazyFuture().recoverWith { case th => pf(th).run() })
  }

  def recover[B >: A](pf: PartialFunction[Throwable, B])(implicit ex: ExecutionContext): LazyFuture[B] = {
    LazyFuture(lazyFuture().recover(pf))
  }

  def tryMap[B](f: Try[A] => B)(implicit ec: ExecutionContext): LazyFuture[B] = {
    LazyFuture(run().tryMap(f))
  }

  def tryFlatMap[B](f: Try[A] => LazyFuture[B])(implicit ec: ExecutionContext): LazyFuture[B] = {
    LazyFuture(run().tryFlatMap(f(_).run()))
  }

  def tryForeach[B](f: Try[A] => B)(implicit ec: ExecutionContext): Unit = {
    lazyFuture().tryForeach(f)
  }
}


object LazyFuture {

  def apply[A](future: => Future[A]): LazyFuture[A] = new LazyFuture[A] {
    override val lazyFuture: () => Future[A] = {
      () => Try(future).recover { case th => Future.failed(th) }.getOrElse(future)
    }
  }

  def apply[A](code: => A)(implicit ec: ExecutionContext): LazyFuture[A] = new LazyFuture[A] {
    override val lazyFuture: () => Future[A] = () => Future(code)
  }

  def successful[A](value: A) = LazyFuture(Future.successful(value))

  def failed(throwable: Throwable) = LazyFuture(Future.failed(throwable))
}
