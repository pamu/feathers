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

package com.missingfuturelib.parallel

import com.missingfuturelib.delayedfuture.DelayedFuture

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

object Implicits {

  implicit class ParallelTraversableFutureImplicit[T](futures: Seq[Future[T]]) {

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

  implicit class FutureImplicit[T](future: Future[T]) {
    def tryFlatMap[U](f: Try[T] => Future[U]): Future[U] = {
      val promise = Promise[U]()
      future.onComplete { result =>
        promise completeWith  f(result)
      }
      promise.future
    }

    def tryMap[U](f: Try[T] => U): Future[U] = {
      val promise = Promise[U]()
      future onComplete { result =>
        promise.success(f(result))
      }
      promise.future
    }
  }

  implicit class ParallelFutureImplicit[T](delayedFuture: DelayedFuture[T]) {

    def retryParallel[U](retries: Int)(future: Future[T] => Future[U])(implicit ec: ExecutionContext): Future[Seq[T]] = {
      val futures = List.fill(retries)(delayedFuture.run())
      Future.sequence(futures)
    }

  }
}
