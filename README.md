# fextensions (Future extensions)

Handy operations on future which are not available in standard scala library

## timeout

Helps timeout an future if its running for too long

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