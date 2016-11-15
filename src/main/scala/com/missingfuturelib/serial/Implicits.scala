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

import scala.concurrent.{ExecutionContext, Future}

object Implicits {

  implicit class DelayedFuturesImplicit[A](delayedFutures: Seq[DelayedFuture[A]]) {

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

  implicit class DelayedFutureImplicit[+A](delayedFuture: DelayedFuture[A]) {

    def retry(retries: Int)(implicit ex: ExecutionContext): DelayedFuture[A] = {
      delayedFuture.recoverWith { case ex =>
        if (retries > 0) delayedFuture
        else DelayedFuture.failed(ex)
      }
    }
  }

}
