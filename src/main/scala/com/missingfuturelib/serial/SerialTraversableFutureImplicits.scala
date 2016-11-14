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

package com.missingfuturelib.serial

import com.missingfuturelib.delayedfuture.DelayedFuture

import scala.collection.TraversableOnce
import scala.concurrent.{ExecutionContext, Future}

object SerialTraversableFutureImplicits {

  implicit class SerialTraversableFutureImplicit[A, T <: TraversableOnce[DelayedFuture[A]]](delayedFutures: T) {

    def foldLeftSerially[B](acc: B)(f: (B, A) => Future[B]): Future[B] = {
      delayedFutures.foldLeft(Future.successful(acc)) { (partialResultFuture, currentFuture) =>
        partialResultFuture.flatMap { partialResult =>
          currentFuture.run().flatMap { current =>
            f(partialResult, current)
          }
        }
      }
    }

    def serialSequence(implicit ec: ExecutionContext): Future[TraversableOnce[A]] =
      serialTraverse(_.map(identity))


    def serialTraverse[B](transform: Future[A] => Future[B])(implicit ec: ExecutionContext): Future[Traversable[B]] = {
      delayedFutures.foldLeft(Future.successful(Traversable.empty[B])) { (partialResultFuture, currentFuture) =>
        partialResultFuture.flatMap { partialResult =>
          transform(currentFuture.run()).map { currentResult =>
            partialResult ++ Traversable(currentResult)
          }
        }
      }
    }

  }

}
