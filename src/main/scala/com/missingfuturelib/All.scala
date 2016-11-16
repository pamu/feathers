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

package com.missingfuturelib

import com.missingfuturelib.delayedfuture.DelayedFuture
import com.missingfuturelib.exceptions.AllFuturesFailedException

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object ec {
  implicit lazy val global = scala.concurrent.ExecutionContext.Implicits.global
}

object All {

  type Task[+A] = DelayedFuture[A]
  type T[+A] = Task[A]

  val Task = DelayedFuture
  val T = DelayedFuture

  type F[+A] = Future[A]
  val F = Future

  implicit class ImplicitForFutures[T](futures: Seq[Future[T]]) {

    private def firstOf(f: (Promise[T], Try[T]) => Unit)(implicit ec: ExecutionContext): Future[T] = {
      val promise = Promise[T]()
      futures.foldLeft(Future.successful(())) { (partialResultFuture, currentFuture) =>

        currentFuture foreach (promise trySuccess)

        partialResultFuture.tryFlatMap(_ => currentFuture.tryMap { currentResult =>
          f(promise, currentResult)
        })

      }.onComplete(_ => promise.tryFailure(AllFuturesFailedException("All futures have failed.")))
      promise.future
    }

    def firstSuccessOf()(implicit ec: ExecutionContext): Future[T] = {
      firstOf { (promise, result) =>
        result match {
          case Success(value) =>
            promise.trySuccess(value)
          case _ => ()
        }
      }
    }

    def firstFailureOf()(implicit ex: ExecutionContext): Future[T] = {
      firstOf { (promise, result) =>
        result match {
          case Failure(th) =>
            promise.tryFailure(th)
          case _ => ()
        }
      }
    }

    def foldLeftParallel[U](acc: U)(f: (U, T) => Future[U])(implicit ec: ExecutionContext): Future[U] = {
      futures.foldLeft(Future.successful(acc)) { (partialResultFuture , currentFuture) =>
        val prF = partialResultFuture
        val cf = currentFuture
        prF.flatMap { partialResult =>
          cf.flatMap(current => f(partialResult, current))
        }
      }
    }

    def foldLeftParallel[U](acc: Future[U])(f: (Future[U], Future[T]) => Future[U])(implicit ec: ExecutionContext): Future[U] =
      futures.foldLeft(acc)(f)

    def onAllComplete()(implicit ec: ExecutionContext): Future[Seq[Try[T]]] = {
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
  }

  implicit class ImplicitForDelayedFuture[T](delayedFuture: DelayedFuture[T]) {

    def retryParallel[U](retries: Int)(implicit ec: ExecutionContext): Future[T] = {
      val futures = List.fill(retries)(delayedFuture.run())
      futures.firstSuccessOf()
    }

    def retry(retries: Int)(implicit ec: ExecutionContext): Future[T] = {
      val promise = Promise[T]()
      def helper(leftOver: Int): Unit = {
        delayedFuture.run().tryForeach {
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

  }

  implicit class ImplicitForDelayedFutures[A](delayedFutures: Seq[DelayedFuture[A]]) {

    def foldLeftSeriallyAsync[B](acc: B)(f: (B, A) => Future[B])(implicit ec: ExecutionContext): Future[B] = {
      delayedFutures.foldLeft(Future.successful(acc)) { (partialResultFuture, currentFuture) =>
        partialResultFuture.flatMap { partialResult =>
          currentFuture.run().flatMap { current =>
            f(partialResult, current)
          }
        }
      }
    }

    def foldLeftSerially[B](acc: B)(f: (B, A) => B)(implicit ec: ExecutionContext): Future[B] = {
      delayedFutures.foldLeft(Future.successful(acc)) { (partialResultFuture, currentFuture) =>
        partialResultFuture.flatMap { partialResult =>
          currentFuture.run().flatMap { current =>
            Future(f(partialResult, current))
          }
        }
      }
    }


    def serialSequence(implicit ec: ExecutionContext): Future[Seq[A]] =
      serialTraverse(_.map(identity))


    def serialTraverse[B](transform: Future[A] => Future[B])(implicit ec: ExecutionContext): Future[Seq[B]] = {
      delayedFutures.foldLeft(Future.successful(Seq.empty[B])) { (partialResultFuture, currentFuture) =>
        partialResultFuture.flatMap { partialResult =>
          transform(currentFuture.run()).map { currentResult =>
            partialResult :+ currentResult
          }
        }
      }
    }

  }
}
