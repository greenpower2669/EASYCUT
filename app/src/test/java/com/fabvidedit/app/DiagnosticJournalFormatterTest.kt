package com.fabvidedit.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticJournalFormatterTest {
    @Test fun errorsAreRedAndNormalLinesGreenInCopiedText() {
        val report = "EASYCUT 0.0.11\n[1] STAGE OPEN_PROJECT\n[2] ERROR export impossible"
        val lines = DiagnosticJournalFormatter.classify(report)
        assertEquals(DiagnosticJournalFormatter.Status.OK, lines[0].status)
        assertEquals(DiagnosticJournalFormatter.Status.INFO, lines[1].status)
        assertEquals(DiagnosticJournalFormatter.Status.ERROR, lines[2].status)
        val copy = DiagnosticJournalFormatter.forCopy(report)
        assertTrue(copy.contains("color:#16803D"))
        assertTrue(copy.contains("color:#D32F2F"))
        assertTrue(copy.contains("[ERREUR]"))
    }

    @Test fun recoveredNonFatalTimeoutIsGrayAndBlockingTimeoutRemainsRed() {
        val lines = DiagnosticJournalFormatter.classify(
            "[1] ERROR optional timeout, fallback recovered\n" +
                "[2] ERROR timeout export impossible"
        )
        assertEquals(DiagnosticJournalFormatter.Status.RECOVERED, lines[0].status)
        assertEquals(DiagnosticJournalFormatter.Status.ERROR, lines[1].status)
        assertTrue(DiagnosticJournalFormatter.forCopy(lines[0].text).contains("color:#737D8C"))
    }

    @Test fun previousErrorFieldsAndStackTracesRemainVisible() {
        val report = "Dernière erreur : Aucune erreur capturée\n" +
            "[1] ERROR stage=EXPORT\n    at Example.test(Example.kt:1)\n" +
            "Motif du dernier arrêt Android : Crash Java/Kotlin"
        val lines = DiagnosticJournalFormatter.classify(report)
        assertEquals(DiagnosticJournalFormatter.Status.INFO, lines[0].status)
        assertEquals(DiagnosticJournalFormatter.Status.ERROR, lines[2].status)
        assertEquals(DiagnosticJournalFormatter.Status.ERROR, lines[3].status)
    }

    @Test fun copiedHtmlEscapesSensitiveContentAndNeverChangesOriginal() {
        val report = "[3] INFO name=<test>&\"demo\""
        val copy = DiagnosticJournalFormatter.forCopy(report)
        assertTrue(copy.contains("name=&lt;test&gt;&amp;&quot;demo&quot;"))
        assertFalse(copy.contains("name=<test>"))
        assertEquals("[3] INFO name=<test>&\"demo\"", report)
    }

    @Test fun exportTraceIsInfoAndDoesNotHideARealError() {
        val log = "[1] EXPORT_TRACE MATRIX sx=1.27\n[2] ERROR export impossible"
        val result = DiagnosticJournalFormatter.classify(log)
        assertEquals(DiagnosticJournalFormatter.Status.INFO, result[0].status)
        assertEquals(DiagnosticJournalFormatter.Status.ERROR, result[1].status)
        assertTrue(DiagnosticJournalFormatter.forCopy(log).contains("EXPORT_TRACE MATRIX"))
    }

    @Test fun clearedJournalNoticeIsGreenInformation() {
        val notice = "Anciennes lignes du journal vidées sur demande (date ms=1790193184044). Le dernier arrêt et la dernière erreur restent conservés."
        assertEquals(DiagnosticJournalFormatter.Status.INFO, DiagnosticJournalFormatter.classify(notice).single().status)
    }

    @Test fun recoveredScanIsNotARealError() {
        val lines = DiagnosticJournalFormatter.classify(
            "[1] STAGE SCAN_SKIPPED_NATIVE_UNVERIFIED\n[2] PERF frames=4"
        )
        assertEquals(DiagnosticJournalFormatter.Status.RECOVERED, lines[0].status)
        assertEquals(DiagnosticJournalFormatter.Status.INFO, lines[1].status)
    }
}
