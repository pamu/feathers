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

import scala.concurrent.{ExecutionContext, Future}

object Implicits {

  implicit class ParallelTraversableFutureImplicit[T, A <: TraversableOnce[Future[T]]](futures: A) {

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

  }

  implicit class ParallelFutureImplicit[T](delayedFuture: DelayedFuture[T]) {

    def retryParallel[U](retries: Int)(future: Future[T] => Future[U])(implicit ec: ExecutionContext): Future[TraversableOnce[T]] = {
      val futures = List.fill(retries)(delayedFuture.run())
      Future.sequence(futures)
    }

  }
}
