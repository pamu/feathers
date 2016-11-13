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

import scala.concurrent.Future

object ParallelTraversableFutureImplicits {
  implicit class ParallelTraversableFutureImplicit[A, T <: Traversable[Future[A]]](futures: T) {
  }
  implicit class ParallelFutureImplicit[T](delayedFuture: DelayedFuture[T]) {
    def retryParallely[U](retries: Int)(future: Future[T] => Future[U]): Future[List[T]] = {
      val futures = List.fill(retries)(delayedFuture.run())
      Future.sequence(futures)
    }
  }
}
