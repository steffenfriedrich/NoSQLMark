package de.unihamburg.informatik.nosqlmark.concurrent

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.ExecutionContext
/**
  * Created by Steffen Friedrich on 05.07.2016.
  */
object IOExecutionContext {
  private def daemonThreadFactory(name: String) = new ThreadFactory {
    private val counter = new AtomicLong(0L)
    override def newThread(r: Runnable) = {
      val thread = new Thread(r)
      thread.setName(s"$name-${counter.getAndIncrement}")
      thread.setDaemon(true)
      thread
    }
  }

  /** Core thread pool to be used for concurrent request-processing */
  private def newIOThreadPool(name: String) = {
    // use direct handoff (SynchronousQueue) + CallerRunsPolicy to avoid deadlocks
    // since tasks may have internal dependencies.
    val pool = new ThreadPoolExecutor(
      /* core size */ 0,
      /* max size */  Integer.MAX_VALUE,
      /* idle timeout */ 12, TimeUnit.SECONDS,
      new SynchronousQueue[Runnable](),
      daemonThreadFactory(name)
    )
    pool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy)
    pool
  }

   implicit val ioThreadPool: ExecutionContext = ExecutionContext.fromExecutorService(newIOThreadPool("async_io"))
}
