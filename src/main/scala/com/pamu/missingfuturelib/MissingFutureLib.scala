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

package com.pamu.missingfuturelib

import scala.concurrent.{ExecutionContext, Future}

object MissingFutureLib {

  object Serial {

    def serialSequence[A](futures: (() => Future[A])*)(implicit ec: ExecutionContext): Future[Traversable[A]] =
      serialTraverse(futures: _*)(_.map(identity))


    def serialTraverse[A, B](futures: (() => Future[A])*)(transform: Future[A] => Future[B])(implicit ec: ExecutionContext): Future[Traversable[B]] = {
      futures.foldLeft(Future.successful(List.empty[B])) { (partialResultFuture, currentFuture) =>
        partialResultFuture.flatMap { partialResult =>
          transform(currentFuture()).map { currentResult =>
            partialResult :+ currentResult
          }
        }
      }
    }

  }

}
