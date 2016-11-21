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

package com.fextensions

import akka.actor.ActorSystem
import com.fextensions.exceptions.{AllFuturesCompleted, AllFuturesFailed, AllFuturesSuccessful, TimeoutException}
import com.fextensions.lazyfuture.LazyFuture

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object ec {
  implicit lazy val global = scala.concurrent.ExecutionContext.Implicits.global
}

object All {

  type Task[+A] = LazyFuture[A]
  type T[+A] = Task[A]

  val Task = LazyFuture
  val T = LazyFuture

  type F[+A] = Future[A]
  val F = Future

  val fextensionsActorSystem = ActorSystem("fextensions-actor-system")

  implicit class ImplicitForFutures[T](futures: Seq[Future[T]]) {

    private def firstOf(f: (Promise[T], Future[T]) => Unit)(onAllComplete: Promise[T] => Unit)(implicit ec: ExecutionContext): Future[T] = {
      val promise = Promise[T]()
      futures.foldLeft(Future.successful(())) { (partialResultFuture, currentFuture) =>
        f(promise, currentFuture)
        partialResultFuture.tryFlatMap(_ => currentFuture.tryMap(_ => ()))
      }.onComplete(_ => onAllComplete(promise))
      promise.future
    }

    def firstCompleteOf(implicit ec: ExecutionContext): Future[T] = {
      firstOf { (promise, future) =>
        future tryForeach (promise tryComplete)
      } { promise =>
        promise.tryFailure(AllFuturesCompleted("Looks like all futures are already completed."))
      }
    }

    def firstSuccessOf(implicit ec: ExecutionContext): Future[T] = {
      firstOf { (promise, future) =>
        future onSuccess { case value => promise trySuccess value }
      } { promise =>
        promise.tryFailure(AllFuturesFailed("Looks like all futures are already completed or All futures failed."))
      }
    }

    def firstFailureOf(implicit ex: ExecutionContext): Future[T] = {
      firstOf { (promise, future) =>
        future onFailure { case th => promise tryFailure th }
      } { promise =>
        promise.tryFailure(AllFuturesSuccessful("Looks like all futures are already completed or All futures successful."))
      }
    }

    def foldLeftParallel[U](seed: U)(f: (Try[U], Try[T]) => Future[U])(implicit ec: ExecutionContext): Future[U] = {
      futures.foldLeft(Future.successful(seed)) { (partialResultFuture , currentFuture) =>
        val prF = partialResultFuture
        val cf = currentFuture
        prF.tryFlatMap { partialResult =>
          cf.tryFlatMap(current => f(partialResult, current))
        }
      }
    }

    def onAllComplete(implicit ec: ExecutionContext): Future[Seq[Try[T]]] = {
      futures.foldLeft(Future.successful(Seq.empty[Try[T]])) { (partialResultFuture, currentFuture) =>
        partialResultFuture.tryFlatMap { (partialResult: Try[Seq[Try[T]]]) =>
          currentFuture.tryMap((currentResult: Try[T]) => partialResult.map(_ :+ currentResult).getOrElse(Seq(currentResult)))
        }
      }
    }

  }

  implicit class ImplicitForFuture[T](future: Future[T]) {

    def tryFlatMap[U](f: Try[T] => Future[U])(implicit ec: ExecutionContext): Future[U] = {
      val promise = Promise[U]()
      future.onComplete { result =>
        promise tryCompleteWith  f(result)
      }
      promise.future
    }

    def tryMap[U](f: Try[T] => U)(implicit ec: ExecutionContext): Future[U] = {
      val promise = Promise[U]()
      future onComplete { result =>
        promise.trySuccess(f(result))
      }
      promise.future
    }

    def tryForeach[U](f: Try[T] => U)(implicit ec: ExecutionContext): Unit = {
      future onComplete { result =>
        f(result)
      }
    }

    def timeout(duration: FiniteDuration)(implicit ec: ExecutionContext): Future[T] = {
      Task(future).timeout(duration)
    }
  }

  implicit class ImplicitForDelayedFuture[T](lazyFuture: LazyFuture[T]) {

    def retryParallel[U](retries: Int)(implicit ec: ExecutionContext): Future[T] = {
      val futures = List.fill(retries)(lazyFuture.run())
      futures.firstSuccessOf
    }

    def retry(retries: Int)(implicit ec: ExecutionContext): Future[T] = {
      val promise = Promise[T]()
      def helper(leftOver: Int): Unit = {
        lazyFuture.run().tryForeach {
          case Success(value) =>
            promise.trySuccess(value)
          case Failure(th) =>
            if (leftOver > 0) helper(leftOver - 1)
            else promise.tryFailure(th)
        }
      }
      helper(retries)
      promise.future
    }

    def timeout(duration: FiniteDuration)(implicit ec: ExecutionContext): Future[T] = {
      val promise = Promise[T]()
      lazyFuture.run().onComplete(promise tryComplete)
      All.fextensionsActorSystem.scheduler.scheduleOnce(duration) {
        promise tryFailure TimeoutException("timed out future couldn't be executed in given duration.")
      }
      promise.future
    }

  }

  implicit class ImplicitForDelayedFutures[A](lazyFutures: Seq[LazyFuture[A]]) {

    def foldLeftSerially[B](acc: B)(f: (Try[B], Try[A]) => Future[B])(implicit ec: ExecutionContext): Future[B] = {

      lazyFutures.foldLeft(Future.successful(acc)) { (partialResultFuture, currentFuture) =>
        partialResultFuture.tryFlatMap { partialResult =>
          currentFuture.run().tryFlatMap { current => f(partialResult, current) }}
      }

    }


    def serialSequence(implicit ec: ExecutionContext): Future[Seq[A]] =
      serialTraverse(_.map(identity))


    def serialTraverse[B](transform: Future[A] => Future[B])(implicit ec: ExecutionContext): Future[Seq[B]] = {
      lazyFutures.foldLeft(Future.successful(Seq.empty[B])) { (partialResultFuture, currentFuture) =>
        partialResultFuture.flatMap { partialResult =>
          transform(currentFuture.run()).map { currentResult =>
            partialResult :+ currentResult
          }
        }
      }
    }

  }
}
