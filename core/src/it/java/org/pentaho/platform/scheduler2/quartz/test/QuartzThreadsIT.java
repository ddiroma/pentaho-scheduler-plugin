/*! ******************************************************************************
 *
 * Pentaho
 *
 * Copyright (C) 2024 by Hitachi Vantara, LLC : http://www.pentaho.com
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE.TXT file.
 *
 * Change Date: 2029-07-20
 ******************************************************************************/


package org.pentaho.platform.scheduler2.quartz.test;

import java.io.IOException;
import java.util.Properties;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.pentaho.platform.api.scheduler2.SchedulerException;
import org.pentaho.platform.engine.core.system.boot.PlatformInitializationException;
import org.pentaho.platform.scheduler2.quartz.QuartzScheduler;

@SuppressWarnings( "nls" )
public class QuartzThreadsIT {

  private QuartzScheduler scheduler;

  private static final String SCHEDULER_NAME = "JUnitTestScheduler";
  private static final int WORKER_THREAD_COUNT = 3;

  @Before
  public void init() throws SchedulerException, PlatformInitializationException, org.quartz.SchedulerException,
    IOException {
    scheduler = new QuartzScheduler();

    Properties props = new Properties();
    props.put( "org.quartz.scheduler.makeSchedulerThreadDaemon", "true" );
    props.put( "org.quartz.threadPool.makeThreadsDaemons", "true" );
    props.put( "org.quartz.scheduler.instanceName", SCHEDULER_NAME );
    props.put( "org.quartz.scheduler.instanceId", "1" );
    props.put( "org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool" );
    props.put( "org.quartz.threadPool.threadCount", new Integer( WORKER_THREAD_COUNT ).toString() );
    props.put( "org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore" );

    System.err.println( "Quartz initialized with properties:" );
    Assert.assertTrue( "Properties object is empty! Something went wrong!", props.size() > 0 );
    props.store( System.out, "QuartzThreadsTest" ); // BEWARE JUnit does not allow exceptions to fail a @Before, so this
                                                    // must be visually inspected

    // org.quartz.scheduler.rmi.export = false
    // org.quartz.scheduler.rmi.proxy = false

    scheduler.setQuartzSchedulerFactory( new org.quartz.impl.StdSchedulerFactory( props ) );
    scheduler.start();
  }

  @After
  public void printThreadDump() {
    Set<Thread> threads = Thread.getAllStackTraces().keySet();
    System.out.println( "LIVE QUARTZ THREADS DUMP:" );
    int lineCount = 0;
    for ( Thread t : threads ) {
      if ( t.isAlive() && t.getName().contains( SCHEDULER_NAME ) ) {
        lineCount++;
        String daemon = ( t.isDaemon() ) ? "(DAEMON)" : "(NON-DAEMON)";
        System.out.println( daemon + " " + t.toString() );
      }
    }
    if ( lineCount == 0 ) {
      System.out.println( "   THERE ARE NO LIVE QUARTZ THREADS" );
    }
  }

  @Test
  public void threadsAreRunningDaemon() {
    Set<Thread> threads = Thread.getAllStackTraces().keySet();
    for ( Thread t : threads ) {
      if ( !t.isDaemon() ) {
        if ( t.getName().contains( SCHEDULER_NAME ) ) {
          Assert.fail( "Thread [" + t + "] should be a daemon" );
        }
      }
    }
  }

  @Test
  public void shutdownKillsAllSchedulerThreads() throws SchedulerException, InterruptedException {
    int preCount = Thread.getAllStackTraces().keySet().size();
    int expectedThreadCount = preCount - 1 - WORKER_THREAD_COUNT; // 1 thread is the quartz scheduler itself
    System.out.println( "Prior to shutdown, thread count is " + preCount );

    scheduler.shutdown();

    for ( int i = 0; i < 30; i++ ) {
      int postCount = Thread.getAllStackTraces().keySet().size();
      if ( expectedThreadCount == postCount ) {
        break;
      }
      Thread.sleep( 1000 ); // allow some time for the threads to be killed. apparently quartz does not wait on them
                            // before returning from shutdown
    }

    int postCount = Thread.getAllStackTraces().keySet().size();
    System.out.println( "After shutdown, thread count is " + postCount );

    Assert.assertEquals( "Shutdown did not kill all of the threads", expectedThreadCount, postCount );

    Set<Thread> threads = Thread.getAllStackTraces().keySet();
    for ( Thread t : threads ) {
      // System.out.println(t.toString());
      if ( t.isAlive() && t.getName().contains( SCHEDULER_NAME ) ) {
        Assert.fail( "Thread [" + t + "] should have been killed during Quartz shutdown" );
      }
    }
  }
}
