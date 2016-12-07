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
  implicit lazy val global: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
}

object All {

  type Task[+A] = LazyFuture[A]
  type T[+A] = Task[A]

  val Task = LazyFuture
  val T = LazyFuture

  type F[+A] = Future[A]
  val F = Future

  object NameConstants {
    final val DEFAULT_ACTION_SYSTEM_NAME = "fextensions-actor-system"
  }

  val fextensionsActorSystem = ActorSystem(NameConstants.DEFAULT_ACTION_SYSTEM_NAME)

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
        future foreach (promise trySuccess)
      } { promise =>
        promise.tryFailure(AllFuturesFailed("All futures failed."))
      }
    }

    def firstFailureOf(implicit ex: ExecutionContext): Future[T] = {
      firstOf { (promise, future) =>
        future.failed.foreach(promise tryFailure)
      } { promise =>
        promise.tryFailure(AllFuturesSuccessful("All futures successful."))
      }
    }

    def foldLeftParallel[U](seed: U)(f: (Try[U], Try[T]) => Future[U])(implicit ec: ExecutionContext): Future[U] = {
      futures.foldLeft(Future.successful(seed)) { (partialResultFuture, currentFuture) =>
        val prF = partialResultFuture
        val cf = currentFuture
        prF.tryFlatMap { partialResult =>
          cf.tryFlatMap(current => f(partialResult, current))
        }
      }
    }

    def onAllComplete[U](f: Future[T] => Future[U])(implicit ec: ExecutionContext): Future[Seq[Try[U]]] = {
      futures.foldLeft(Future.successful(Seq.empty[Try[U]])) { (partialResultFuture, currentFuture) =>
        partialResultFuture.tryFlatMap { (partialResult: Try[Seq[Try[U]]]) =>
          f(currentFuture).tryMap((currentResult: Try[U]) => partialResult.map(_ :+ currentResult).getOrElse(Seq(currentResult)))
        }
      }
    }

    def onAllComplete(implicit ec: ExecutionContext): Future[Seq[Try[T]]] = {
      onAllComplete(identity(_))
    }

    def onEachCompletion[U](f: Try[T] => Future[U])(implicit ec: ExecutionContext): Future[Seq[Try[T]]] = {
      onAllComplete { currentFuture =>
        currentFuture.tryFlatMap { value =>
          f(value).tryFlatMap { _ =>
            value match {
              case Success(successValue) => Future.successful(successValue)
              case Failure(th) => Future.failed(th)
            }
          }
        }
      }
    }

  }

  implicit class ImplicitForFuture[T](future: Future[T]) {

    def tryFlatMap[U](f: Try[T] => Future[U])(implicit ec: ExecutionContext): Future[U] = {
      val promise = Promise[U]()
      future.onComplete { result =>
        promise tryCompleteWith f(result)
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

  implicit class ImplicitForLazyFuture[T](lazyFuture: LazyFuture[T]) {

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
        promise tryFailure TimeoutException(s"Future timeout after ${duration.toString()}")
      }
      promise.future
    }

  }

  implicit class ImplicitForLazyFutures[A](lazyFutures: Seq[LazyFuture[A]]) {

    def foldLeftSerial[B](acc: B)(f: (Try[B], Try[A]) => LazyFuture[B])(implicit ec: ExecutionContext): LazyFuture[B] = {
      lazyFutures.foldLeft(LazyFuture.successful(acc)) { (partialResultFuture, currentFuture) =>
        partialResultFuture.tryFlatMap { partialResult =>
          currentFuture.tryFlatMap { current => f(partialResult, current)
          }
        }
      }
    }

    def foldLeftParallel[B](acc: B)(f: (Try[B], Try[A]) => LazyFuture[B])(implicit ec: ExecutionContext): LazyFuture[B] = {
      lazyFutures.foldLeft(LazyFuture.successful(acc)) { (partialResultFuture, currentFuture) =>
        partialResultFuture.run()
        currentFuture.run()
        partialResultFuture.tryFlatMap { partialResult =>
          currentFuture.tryFlatMap { current => f(partialResult, current)
          }
        }
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
