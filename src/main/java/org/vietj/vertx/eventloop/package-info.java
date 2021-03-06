/**
 * = Demystifying the Event Loop
 * Julien Viet <julien@julienviet.com>
 *
 * The event loop plays a key role in Vert.x for writing highly scalable and performant network applications.
 *
 * The event loop is inherited from the Netty library on which Vert.x is based.
 *
 * We often use the expression _running on the event loop_, it has a very specific meaning: it means that the
 * current Thread is an event loop thread. This article provides an overview of the Vert.x event loop and the concepts
 * related to it.
 *
 * == The golden rule
 *
 * When using Vert.x there is one Vert.x golden rule to respect:
 *
 * [quote, Tim Fox]
 * Never block the event loop!
 *
 * The code executed on the event loop should never block the event loop, for instance:
 *
 * - using a blocking method directly or, for instance, reading a file with the `java.io.FileInputStream` api
 *   or a JDBC connection.
 * - doing a long and CPU intensive task
 *
 * When the event loop is blocked:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.BlockingEventLoop#main}
 * ----
 *
 * Vert.x will detect it and log a warn:
 *
 * ----
 * WARNING: Thread Thread[vert.x-eventloop-thread-1,5,main] has been blocked for 2616 ms time 2000000000
 * Apr 04, 2015 1:18:43 AM io.vertx.core.impl.BlockedThreadChecker
 * WARNING: Thread Thread[vert.x-eventloop-thread-1,5,main] has been blocked for 3617 ms time 2000000000
 * Apr 04, 2015 1:18:44 AM io.vertx.core.impl.BlockedThreadChecker
 * WARNING: Thread Thread[vert.x-eventloop-thread-1,5,main] has been blocked for 4619 ms time 2000000000
 * java.lang.Thread.sleep(Native Method)
 * Apr 04, 2015 1:18:45 AM io.vertx.core.impl.BlockedThreadChecker
 * WARNING: Thread Thread[vert.x-eventloop-thread-1,5,main] has been blocked for 5620 ms time 2000000000
 * io.vertx.example.BlockingEventLoop.start(BlockingEventLoop.java:19)
 * io.vertx.core.AbstractVerticle.start(AbstractVerticle.java:111)
 * io.vertx.core.impl.DeploymentManager.lambda$doDeploy$88(DeploymentManager.java:433)
 * io.vertx.core.impl.DeploymentManager$$Lambda$4/2141179775.handle(Unknown Source)
 * io.vertx.core.impl.ContextImpl.lambda$wrapTask$3(ContextImpl.java:263)
 * io.vertx.core.impl.ContextImpl$$Lambda$5/758013696.run(Unknown Source)
 * io.netty.util.concurrent.SingleThreadEventExecutor.runAllTasks(SingleThreadEventExecutor.java:380)
 * io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:357)
 * io.netty.util.concurrent.SingleThreadEventExecutor$2.run(SingleThreadEventExecutor.java:116)
 * java.lang.Thread.run(Thread.java:745)
 * ----
 *
 * The event loop must not be blocked, because it will freeze the parts of the applications using that event loop, with
 * severe consequences on the scalability and the throughput of the application.
 *
 * == The Context
 *
 * Beyond the event loop, Vert.x defines the notion of a context. At a high level, the context can be thought of as
 * controlling the scope and order in which a set of handlers are executed.
 *
 * Contexts are directly associated with `Verticle instances as well. Any handler registered within a verticle - whether
 * it be an event bus consumer, HTTP server handler, or any other asynchronous operation - will be registered using
 * the verticle’s context. This behavior allows for a greatly simplified threading model by guaranteeing that
 * associated handlers will always be executed on the same thread, thus removing the need for synchronization and
 * other locking mechanisms.
 *
 * Vert.x provides three different types of contexts.
 *
 * - Event loop context
 * - Worker context
 * - Multi-threaded worker context
 *
 * === Event loop context
 *
 * An event loop context executes handlers on an event loop. This is the type of context that is the default and most
 * commonly used type of context. Verticles deployed without the worker flag will always be deployed with an event
 * loop context.
 *
 * When Vert.x creates an event loop context, it chooses an event loop for this context, the event loop is chosen via a round
 * robin algorithm. This can be demonstrated by deploying the same verticle many times:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.CreatingManyEventLoops#main}
 * ----
 *
 * The result is:
 *
 * ----
 * Thread[main,5,main]
 * 0:Thread[vert.x-eventloop-thread-0,5,main]
 * 11:Thread[vert.x-eventloop-thread-11,5,main]
 * 10:Thread[vert.x-eventloop-thread-10,5,main]
 * 13:Thread[vert.x-eventloop-thread-13,5,main]
 * 12:Thread[vert.x-eventloop-thread-12,5,main]
 * 14:Thread[vert.x-eventloop-thread-14,5,main]
 * 16:Thread[vert.x-eventloop-thread-0,5,main]
 * 6:Thread[vert.x-eventloop-thread-6,5,main]
 * 15:Thread[vert.x-eventloop-thread-15,5,main]
 * 5:Thread[vert.x-eventloop-thread-5,5,main]
 * 4:Thread[vert.x-eventloop-thread-4,5,main]
 * 3:Thread[vert.x-eventloop-thread-3,5,main]
 * 2:Thread[vert.x-eventloop-thread-2,5,main]
 * 1:Thread[vert.x-eventloop-thread-1,5,main]
 * 17:Thread[vert.x-eventloop-thread-1,5,main]
 * 18:Thread[vert.x-eventloop-thread-2,5,main]
 * 19:Thread[vert.x-eventloop-thread-3,5,main]
 * 9:Thread[vert.x-eventloop-thread-9,5,main]
 * 8:Thread[vert.x-eventloop-thread-8,5,main]
 * 7:Thread[vert.x-eventloop-thread-7,5,main]
 * ----
 *
 * After sorting the result:
 *
 * ----
 * Thread[main,5,main]
 * 0:Thread[vert.x-eventloop-thread-0,5,main]
 * 1:Thread[vert.x-eventloop-thread-1,5,main]
 * 2:Thread[vert.x-eventloop-thread-2,5,main]
 * 3:Thread[vert.x-eventloop-thread-3,5,main]
 * 4:Thread[vert.x-eventloop-thread-4,5,main]
 * 5:Thread[vert.x-eventloop-thread-5,5,main]
 * 6:Thread[vert.x-eventloop-thread-6,5,main]
 * 7:Thread[vert.x-eventloop-thread-7,5,main]
 * 8:Thread[vert.x-eventloop-thread-8,5,main]
 * 9:Thread[vert.x-eventloop-thread-9,5,main]
 * 10:Thread[vert.x-eventloop-thread-10,5,main]
 * 11:Thread[vert.x-eventloop-thread-11,5,main]
 * 12:Thread[vert.x-eventloop-thread-12,5,main]
 * 13:Thread[vert.x-eventloop-thread-13,5,main]
 * 14:Thread[vert.x-eventloop-thread-14,5,main]
 * 15:Thread[vert.x-eventloop-thread-15,5,main]
 * 16:Thread[vert.x-eventloop-thread-0,5,main]
 * 17:Thread[vert.x-eventloop-thread-1,5,main]
 * 18:Thread[vert.x-eventloop-thread-2,5,main]
 * 19:Thread[vert.x-eventloop-thread-3,5,main]
 * ----
 *
 * As we can see we obtained different event loop threads for each Verticle and the thread are obtained with
 * a round robin policy. Note that the number of event loop threads by default depends on your CPU but this can
 * be configured.
 *
 * An event loop context guarantees to always use the same thread, however the converse is not true: the same thread
 * can be used by different event loop contexts. The previous example shows clearly that a same thread is used
 * for different event loops by the Round Robin policy.
 *
 * todo: Configuring the event loop, talk about the options for configuring the event loop size, etc...
 *
 * === Worker context
 *
 * Worker contexts are assigned to verticles deployed with the worker option enabled. The worker context is
 * differentiated from standard event loop contexts in that workers are executed on a separate worker thread pool.
 * This separation from event loop threads allows worker contexts to execute the types of blocking operations that
 * will block the event loop.
 *
 * A worker context, cannot create servers or clients, since they require an event loop context:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.ServerStartingFromWorker#main}
 * ----
 *
 * Vert.x will fail:
 *
 * ----
 * SEVERE: Cannot use HttpServer in a worker verticle
 * java.lang.IllegalStateException: Cannot use HttpServer in a worker verticle
 * at io.vertx.core.http.impl.HttpServerImpl.<init>(HttpServerImpl.java:130)
 * at io.vertx.core.impl.VertxImpl.createHttpServer(VertxImpl.java:249)
 * at io.vertx.core.impl.VertxImpl.createHttpServer(VertxImpl.java:254)
 * at org.vietj.vertx.eventloop.ServerStartedFromWorker.start(ServerStartedFromWorker.java:19)
 * at io.vertx.core.AbstractVerticle.start(AbstractVerticle.java:111)
 * at io.vertx.core.impl.DeploymentManager.lambda$doDeploy$88(DeploymentManager.java:433)
 * at io.vertx.core.impl.DeploymentManager$$Lambda$2/1792845110.handle(Unknown Source)
 * at io.vertx.core.impl.ContextImpl.lambda$wrapTask$3(ContextImpl.java:263)
 * at io.vertx.core.impl.ContextImpl$$Lambda$3/381707837.run(Unknown Source)
 * at io.vertx.core.impl.OrderedExecutorFactory$OrderedExecutor.lambda$new$180(OrderedExecutorFactory.java:91)
 * at io.vertx.core.impl.OrderedExecutorFactory$OrderedExecutor$$Lambda$1/1211888640.run(Unknown Source)
 * at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
 * at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
 * at java.lang.Thread.run(Thread.java:745)
 * ----
 *
 * The only eligible way to communicate with other Vert.x component is via the event bus, a worker is allowed
 * to send a message or reply to an incoming message.
 *
 * A common pattern is to deploy worker verticles and send them a message and then the worker replies to this message:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.WorkerReplying#main}
 * ----
 *
 * This prints:
 *
 * ----
 * Executed by Thread[vert.x-worker-thread-1,5,main]
 * Executed by Thread[vert.x-worker-thread-2,5,main]
 * Executed by Thread[vert.x-worker-thread-3,5,main]
 * Executed by Thread[vert.x-worker-thread-4,5,main]
 * Executed by Thread[vert.x-worker-thread-5,5,main]
 * Executed by Thread[vert.x-worker-thread-6,5,main]
 * Executed by Thread[vert.x-worker-thread-7,5,main]
 * Executed by Thread[vert.x-worker-thread-8,5,main]
 * Executed by Thread[vert.x-worker-thread-9,5,main]
 * Executed by Thread[vert.x-worker-thread-10,5,main]
 * Executed by Thread[vert.x-worker-thread-11,5,main]
 * ----
 *
 * The previous example clearly shows that the worker context of the verticle use different worker threads
 * for delivering the messages:
 *
 * Just as is the case with the event loop context, worker contexts ensure that handlers are only executed on one
 * thread at any given time. That is, handlers executed on a worker context will always be executed
 * sequentially - one after the other - but different actions may be executed on different threads.
 *
 * However the same thread can be used by several worker verticles:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.WorkerInstancesReplyingLowThreads#main}
 * ----
 *
 * This prints:
 *
 * ----
 * Executed by worker 1 with Thread[vert.x-worker-thread-1,5,main]
 * Executed by worker 2 with Thread[vert.x-worker-thread-0,5,main]
 * Executed by worker 1 with Thread[vert.x-worker-thread-1,5,main]
 * Executed by worker 2 with Thread[vert.x-worker-thread-0,5,main]
 * Executed by worker 1 with Thread[vert.x-worker-thread-1,5,main]
 * Executed by worker 2 with Thread[vert.x-worker-thread-0,5,main]
 * Executed by worker 3 with Thread[vert.x-worker-thread-1,5,main]
 * Executed by worker 4 with Thread[vert.x-worker-thread-0,5,main]
 * Executed by worker 3 with Thread[vert.x-worker-thread-1,5,main]
 * Executed by worker 4 with Thread[vert.x-worker-thread-0,5,main]
 * ----
 *
 * The same worker verticle class can be deployed several times by specifying the number of instances. This allows
 * to concurrently process blocking tasks:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.WorkerInstancesReplying#main}
 * ----
 *
 * Workers can schedule timers:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.TimerOnWorkerThread#main}
 * ----
 *
 * This prints:
 *
 * ----
 * Starting timer on Thread[vert.x-worker-thread-0,5,main]
 * Timer fired Thread[vert.x-worker-thread-1,5,main] after 1004 ms
 * ----
 *
 * Again the timer thread is not the same than the thread that created the timer.
 *
 * With a periodic timer
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.PeriodicOnWorkerThread#main}
 * ----
 *
 * we get a different thread for each event:
 *
 * ----
 * Starting periodic on Thread[vert.x-worker-thread-0,5,main]
 * Periodic fired Thread[vert.x-worker-thread-1,5,main] after 1004 ms
 * Periodic fired Thread[vert.x-worker-thread-2,5,main] after 2004 ms
 * Periodic fired Thread[vert.x-worker-thread-3,5,main] after 3004 ms
 * Periodic fired Thread[vert.x-worker-thread-4,5,main] after 4006 ms
 * Periodic fired Thread[vert.x-worker-thread-5,5,main] after 5004 ms
 * Periodic fired Thread[vert.x-worker-thread-6,5,main] after 6005 ms
 * Periodic fired Thread[vert.x-worker-thread-7,5,main] after 7004 ms
 * Periodic fired Thread[vert.x-worker-thread-8,5,main] after 8005 ms
 * Periodic fired Thread[vert.x-worker-thread-9,5,main] after 9005 ms
 * Periodic fired Thread[vert.x-worker-thread-10,5,main] after 10006 ms
 * Periodic fired Thread[vert.x-worker-thread-11,5,main] after 11006 ms
 * ----
 *
 * Since the worker thread may block, the delivery cannot be guaranteed in time:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.TimerOnWorkerThreadNotGuaranted#main}
 * ----
 *
 * This prints:
 *
 * ----
 * Starting timer on Thread[vert.x-worker-thread-0,5,main]
 * Timer fired Thread[vert.x-worker-thread-0,5,main] after 2007 ms
 * ----
 *
 * Todo : talk about worker instances.
 *
 * === Multi-threaded worker context
 *
 * Multi-threaded contexts are assigned to verticles deployed with the multi-threaded option enabled. Whereas standard
 * worker contexts execute actions in order on a variety of threads, the multi-threaded worker context removes the
 * strong ordering of events to allow the execution of multiple events concurrently. This means that the user is
 * responsible for performing the appropriate concurrency control such as synchronization and locking.
 *
 * todo
 *
 * == Dealing with contexts
 *
 * Using a context is usually transparent, Vert.x will manage contexts implicitly when deploying a Verticle,
 * registering an Event Bus handler, etc... However the Vert.x API provides several ways to interact with a Context
 * allowing for manual context switching.
 *
 * === The current context
 *
 * The static `Vertx.currentContext()` methods returns the current context if there is one, it returns null otherwise.
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.CurrentContextFromMain#main}
 * ----
 *
 * We get obviously `null` no matter the Vertx instance we created before:
 *
 * ----
 * Current context is null
 * ----
 *
 * Now the same from a verticle leads to obtaining the Verticle context:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.CurrentContextFromVerticle#main}
 * ----
 *
 * We get:
 *
 * ----
 * Current context is io.vertx.core.impl.EventLoopContext@424ff050
 * Verticle context is io.vertx.core.impl.EventLoopContext@424ff050
 * ----
 *
 * === Creating or reusing a context
 *
 * The `vertx.getOrCreateContext()` returns the context associated with the thread (like `currentContext`) otherwise
 * it creates a new context, associates it to event loop and returns it:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.GettingOrCreatingContextFromMain#main}
 * ----
 *
 * Note, that creating a context, will not associate the current thread with this context. This will indeed not
 * change the nature of the current thread! However we can now use this context for running an action:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.CreatingAndUsingContextFromMain#main}
 * ----
 *
 * This prints:
 *
 * ----
 * Current context is io.vertx.core.impl.EventLoopContext@17979104
 * ----
 *
 * Calling `getOrCreateContext` from a verticle returns the context associated with the Verticle:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.GettingOrCreatingContextFromVerticle#main}
 * ----
 *
 * This prints:
 *
 * ----
 * io.vertx.core.impl.EventLoopContext@10b02dc5
 * io.vertx.core.impl.EventLoopContext@10b02dc5
 * ----
 *
 * === Running on context
 *
 * The `io.vertx.core.Context.runOnContext(Handler)` method can be used when the thread attached to the context needs
 * to run a particular task on a context.
 *
 * For instance, the context thread initiates a non Vert.x action, when this action ends it needs to do update some
 * state and it needs to be done with the context thread to guarantee that the state will be visible by the
 * context thread.
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.RunningOnContext#start()}
 * ----
 *
 * This prints:
 *
 * ----
 * Running with context : io.vertx.core.impl.EventLoopContext@69cdd6d8
 * Current context : null
 * Runs on the original context : io.vertx.core.impl.EventLoopContext@69cdd6d8
 * ----
 *
 * The `vertx.runOnContext(Handler<Void>)` is a shortcut for what we have seen before: it calls the
 * `getOrCreateContext` method and schedule a task for execution via the `context.runOnContext(Handler<Void>)` method.
 *
 * == Verticles
 *
 * Vert.x guarantees that the same Verticle will always be called from the same thread, whether or not the Verticle
 * is deployed as a worker or not. This implies that any service created from a Verticle will reuse the same context,
 * for instance:
 *
 * - Creating a server
 * - Creating a client
 * - Creating a timer
 * - Registering an event but handler
 * - etc...
 *
 * Such _services_ will call back the Verticle that created them at some point, when this happens it will be with
 * the *exact same thread*, whether this is an event loop context or a worker context.
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.ContextPropagationFromVerticle#main}
 * ----
 *
 * This prints:
 *
 * ----
 * Starting verticle with Thread[vert.x-eventloop-thread-1,5,main]
 * Got reply on Thread[vert.x-eventloop-thread-1,5,main]
 * ----
 *
 * == Embedding Vert.x
 *
 * When Vert.x is embedded like in a _main_ Java method, the thread creating Vert.x can be any kind of thread, but
 * it is certainly not a Vert.x thread. Any action that requires a context will implicitly create a context for
 * achieving this action.
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.CreatingAnEventLoopFromHttpServer#main}
 * ----
 *
 * When several actions are done, there will use different context and there are high chances they will use a
 * different event loop thread.
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.CreatingDifferentEventLoopsFromHttpServers#main}
 * ----
 *
 * This prints:
 *
 * ----
 * Current thread is Thread[vert.x-eventloop-thread-1,5,main]
 * Current thread is Thread[vert.x-eventloop-thread-0,5,main]
 * ----
 *
 * Therefore accessing a shared state from both servers should not be done!
 *
 * When the same context needs to be used then the actions can be grouped with a `runOnContext` call:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.UsingEventLoopsFromHttpServers#main}
 * ----
 *
 * This prints:
 *
 * ----
 * Current thread is Thread[vert.x-eventloop-thread-0,5,main]
 * Current thread is Thread[vert.x-eventloop-thread-0,5,main]
 * ----
 *
 * Now we can share state between the two servers safely.
 *
 * == Blocking
 *
 * Before Vert.x 3, using blocking API required to deploy a worker Verticle. Vert.x 3 provides an additional API
 * for using a blocking API:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.ExecuteBlockingSuccess#main}
 * ----
 *
 * This prints:
 *
 * ----
 * Calling blocking block from Thread[vert.x-eventloop-thread-0,5,main]
 * Computing with Thread[vert.x-worker-thread-0,5,main]
 * Got result in Thread[vert.x-eventloop-thread-0,5,main]
 * ----
 *
 * While the blocking code handler executes with a worker thread, the result handler is executed with the same event
 * loop context.
 *
 * The blocking code handler is provided a `Future` argument that is used for signaling when the result is obtained,
 * usually a result of the blocking API.
 *
 * When the blocking code handler fails the result handler will get the failure as cause of the async result object:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.ExecuteBlockingThrowingFailure#main}
 * ----
 *
 * This prints:
 *
 * ----
 * Blocking code failed
 * java.lang.RuntimeException
 * at org.vietj.vertx.eventloop.ExecuteBlockingThrowingFailure.lambda$null$0(ExecuteBlockingThrowingFailure.java:19)
 * at org.vietj.vertx.eventloop.ExecuteBlockingThrowingFailure$$Lambda$4/163784093.handle(Unknown Source)
 * at io.vertx.core.impl.ContextImpl.lambda$executeBlocking$2(ContextImpl.java:217)
 * * at io.vertx.core.impl.ContextImpl$$Lambda$6/1645685573.run(Unknown Source)
 * at io.vertx.core.impl.OrderedExecutorFactory$OrderedExecutor.lambda$new$180(OrderedExecutorFactory.java:91)
 * at io.vertx.core.impl.OrderedExecutorFactory$OrderedExecutor$$Lambda$2/1053782781.run(Unknown Source)
 * at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
 * at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
 * at java.lang.Thread.run(Thread.java:745)
 * ----
 *
 * The blocking code handler can also report the failure on the `Future` object:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.ExecuteBlockingFailingFuture#main}
 * ----
 *
 * This API is somewhat similar to deploying a worker Verticle, however it does not provide any configurability
 * about the number of instances, like a worker Verticle provides.
 *
 */
@Document(fileName = "Demystifying_the_event_loop.adoc")
package org.vietj.vertx.eventloop;

import io.vertx.docgen.Document;