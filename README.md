# fextensions (Future extensions) [![](https://jitpack.io/v/pamu/fextensions.svg)](https://jitpack.io/#pamu/fextensions)

Handy operations on future which are not available in standard scala library. 


add fextensions as a library dependency

```scala

resolvers += "jitpack" at "https://jitpack.io"
        
libraryDependencies += "com.github.pamu" % "fextensions" % "0.0.1-v1-SNAPSHOT"

```

Documentation

1. [onAllComplete](#onallcomplete) Wait in a non blocking way till all futures are complete

2. [Timeout](#timeout) Timeout after given duration if future does not complete

3. [tryMap](#trymap) Handle both success and failure cases with map, no need to use recover.

4. [tryFlatMap](#tryflatmap) flatMap which handles both the success and failure case.

5. [retry](#retry) retry a future till it is successful.

## onAllComplete

Wait in a non blocking way till all futures are complete

### onAllComplete implementation: 

```scala

   def onAllComplete(implicit ec: ExecutionContext): Future[Seq[Try[T]]] = {
      futures.foldLeft(Future.successful(Seq.empty[Try[T]])) { (partialResultFuture, currentFuture) =>
        partialResultFuture.tryFlatMap { (partialResult: Try[Seq[Try[T]]]) =>
          currentFuture.tryMap((currentResult: Try[T]) => partialResult.map(_ :+ currentResult).getOrElse(Seq(currentResult)))
        }
      }
    }

```

### usage:

```scala

import com.fextensions.All._
import com.fextensions.ec._
import scala.concurrent.duration._

val f = F {
 Thread.sleep(1000)
 1
} //completes after 1 sec

val g = F {
 Thread.sleep(5000)
}
//completes after 5 seconds
 
val r = List(f, g)

r.onAllComplete onComplete println

//r completes after 5 seconds after all futures are completed 

```
## timeout

Helps timeout an future if its running for too long

### timeout implementation:

```scala

def timeout(duration: FiniteDuration)(implicit ec: ExecutionContext): Future[T] = {
  val promise = Promise[T]()
  lazyFuture.run().onComplete(promise tryComplete)
  All.fextensionsActorSystem.scheduler.scheduleOnce(duration) {
    promise tryFailure TimeoutException(s"Future timeout after ${duration.toString()}")
  }
  promise.future
}

```

### usage:
```scala
import com.fextensions.All._
import com.fextensions.ec.global
import scala.concurrent.duration._

//F is a shortcut for future
val longRunningWork = F {
  Thread.sleep(10000)
}

val fastFuture = F {
 Thread.sleep(1000)
}

longRunningWork.timeout(2 seconds).fallbackTo(fastFuture)

//The above call will fallback to fastFuture if longRunningWork takes more than 2 seconds.


```

## tryMap 

map on future only helps to handle the positive case when the future is successful. But tryMap helps
handle both the success and failure case without using recover

### tryMap implementation:

```scala

def tryMap[U](f: Try[T] => U)(implicit ec: ExecutionContext): Future[U] = {
  val promise = Promise[U]()
  future onComplete { result =>
    promise.trySuccess(f(result))
  }
  promise.future
}

```

### usage:

```scala
//F is a shortcut for Future

import com.fextensions.ec.global //execution context
import com.fextensions.All._ //get all methods and aliases into scope
import scala.util._

F {
  Thread.sleep(10000)
  1L
}.tryMap {
 case Success(value) => // handle positive case
 case Failure(th) => //handle negative case
}

//No need for recover 


```

## tryFlatMap

flatMap defined on future only allows you to  

### tryFlatMap implementation:

```scala

def tryFlatMap[U](f: Try[T] => Future[U])(implicit ec: ExecutionContext): Future[U] = {
  val promise = Promise[U]()
  future.onComplete { result =>
    promise tryCompleteWith  f(result)
  }
  promise.future
}

```

### usage:

```scala

import com.fextensions.ec.global //execution context
import com.fextensions.All._ //get all methods and aliases into scope
import scala.util._

val f = F {
  Thread.sleep(10000)
  1
}

f.tryFlatMap {
 case Success(value) => F.successful(value)
 case Failure(th) => F.successful(0)
}


```

## retry

retry a future until successful providing max retry limit

### retry implementation

```scala

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

```

## usage:

```scala                                                             

import com.fextensions.ec.global //execution context                  
import com.fextensions.All._ //get all methods and aliases into scope 
import scala.util._

val f =
F {
  doSomeStuff()
}


f.retry(3) //tries for 3 times if f keeps on failing


```