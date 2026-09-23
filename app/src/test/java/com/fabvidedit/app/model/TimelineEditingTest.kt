package com.fabvidedit.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TimelineEditingTest {
    @Test fun rippleDeleteClosesSplitGapOnSameTrack() {
        val left = VideoClip(id="left", sourceId="s0", sourceStreamIndex=0, timelineTrackIndex=0, timelineStartMs=0, uri="content://left", name="left", durationMs=8_000, trimStartMs=0, trimEndMs=4_000)
        val right = left.copy(id="right", uri="content://right", name="right", timelineStartMs=4_000, trimStartMs=4_000, trimEndMs=8_000)
        val result = VideoProject(name="ripple", clips=listOf(left,right), timelineMode=TimelineMode.MULTITRACK).rippleDeleteClip("left")
        assertEquals(1, result.clips.size); assertEquals("right", result.clips.single().id); assertEquals(0L, result.clips.single().timelineStartMs)
    }
    @Test fun rippleDeleteDoesNotMoveOtherTracks() {
        val a=VideoClip(id="a",uri="content://a",name="a",durationMs=2_000,timelineTrackIndex=0)
        val b=VideoClip(id="b",uri="content://b",name="b",durationMs=2_000,timelineTrackIndex=0,timelineStartMs=2_000)
        val c=VideoClip(id="c",uri="content://c",name="c",durationMs=5_000,timelineTrackIndex=1,timelineStartMs=1_000)
        val result=VideoProject(name="ripple",clips=listOf(a,b,c),timelineMode=TimelineMode.MULTITRACK).rippleDeleteClip("a")
        assertEquals(0L,result.clips.first{it.id=="b"}.timelineStartMs); assertEquals(1_000L,result.clips.first{it.id=="c"}.timelineStartMs)
    }
    @Test fun dragDropMovesClipInTimeAndToAnotherTrack() {
        val base=VideoClip(id="base",uri="content://base",name="base",durationMs=10_000,timelineTrackIndex=0)
        val moving=VideoClip(id="moving",uri="content://moving",name="moving",durationMs=2_000,timelineTrackIndex=1)
        val result=VideoProject(name="move",clips=listOf(base,moving),timelineMode=TimelineMode.MULTITRACK).moveClipOnTimeline("moving",2,3_500)
        val moved=result.clips.first{it.id=="moving"}; assertEquals(3_500L,moved.timelineStartMs); assertNotEquals(result.clips.first{it.id=="base"}.timelineTrackIndex,moved.timelineTrackIndex); assertEquals(9,result.projectFormatVersion)
    }
    @Test fun appendToMainTrackAfterFirstVideoKeepsOtherClipsAndExtendsProjectWhenNeeded() {
        val first = VideoClip(id="first", uri="file://one", name="one",
            durationMs=3_000, timelineTrackIndex=0, timelineStartMs=0L)
        val second = VideoClip(id="second", uri="file://two", name="two",
            durationMs=6_000, timelineTrackIndex=1, timelineStartMs=4_000L)
        val source = VideoProject(name="suite", clips=listOf(first, second),
            timelineMode=TimelineMode.MULTITRACK)
        val next = source.appendClipToVideoTrack("second", 0)
        val moved = next.clips.single { it.id=="second" }
        assertEquals(0, moved.timelineTrackIndex)
        assertEquals(3_000L, moved.timelineStartMs)
        assertEquals(9_000L, next.durationMs)
        assertEquals(first, next.clips.single { it.id == "first" })
        assertEquals(2, next.clips.size)
        assertEquals(source, source.appendClipToVideoTrack("nonexistent", 0))
    }

    @Test fun draggingOntoV1AfterOccupiedClipNeverCreatesOverlapOrRenumbers() {
        val first=VideoClip(id="first",uri="file://first",name="first",
            durationMs=3_000,timelineTrackIndex=0)
        val second=VideoClip(id="second",uri="file://second",name="second",
            durationMs=6_000,timelineTrackIndex=1,timelineStartMs=4_000)
        val before=VideoProject(name="drop",clips=listOf(first,second),
            timelineMode=TimelineMode.MULTITRACK)
        val after=before.moveClipOnTimeline("second",0,2_900L)
        assertEquals(0,after.clips.first { it.id=="second" }.timelineTrackIndex)
        assertEquals(3_000L,after.clips.first { it.id=="second" }.timelineStartMs)
        assertEquals(9_000L,after.durationMs)
    }

}
