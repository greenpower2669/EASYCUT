package com.fabvidedit.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class FfprobeNativeGateTest {
    @Test fun concurrentProbeCallsDoNotOverlapAndGateReleasesOnError() {
        val simultaneous = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val ready = CountDownLatch(6)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(6)
        try {
            val workers = (1..6).map {
                pool.submit(Callable {
                    ready.countDown()
                    if (!start.await(5, TimeUnit.SECONDS)) error("timeout waiting for start")
                    FfprobeNativeGate.run {
                        val current = simultaneous.incrementAndGet()
                        peak.updateAndGet { maxOf(it, current) }
                        try { Thread.sleep(5) } finally { simultaneous.decrementAndGet() }
                    }
                })
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            workers.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, peak.get())
            assertEquals(0, simultaneous.get())
            runCatching { FfprobeNativeGate.run { error("sentinel") } }
            assertEquals(42, FfprobeNativeGate.run { 42 })
        } finally {
            start.countDown()
            pool.shutdownNow()
        }
    }
}
