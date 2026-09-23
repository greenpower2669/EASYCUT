package com.fabvidedit.app

import android.app.Application
import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabvidedit.app.data.ProjectStore
import com.fabvidedit.app.media.ExportManager
import com.fabvidedit.app.media.ExportSettings
import com.fabvidedit.app.media.ExportResolution
import com.fabvidedit.app.media.ExportState
import com.fabvidedit.app.media.ImportedMediaContainer
import com.fabvidedit.app.media.MediaImportPipeline
import com.fabvidedit.app.media.MediaInspector
import com.fabvidedit.app.media.ProjectStorageManager
import com.fabvidedit.app.model.AspectRatioPreset
import com.fabvidedit.app.model.AudioKeyframe
import com.fabvidedit.app.model.AudioTrack
import com.fabvidedit.app.model.ClipFilter
import com.fabvidedit.app.model.ClipTransform
import com.fabvidedit.app.model.ClipTransition
import com.fabvidedit.app.model.MotionEasing
import com.fabvidedit.app.model.SourceAudioKeyframe
import com.fabvidedit.app.model.SourceAudioTrack
import com.fabvidedit.app.model.TextLayer
import com.fabvidedit.app.model.TimelineMode
import com.fabvidedit.app.model.TransformKeyframe
import com.fabvidedit.app.model.TransitionType
import com.fabvidedit.app.model.VideoClip
import com.fabvidedit.app.model.VideoLayerPolicy
import com.fabvidedit.app.model.VideoProject
import com.fabvidedit.app.model.VisualMediaKind
import com.fabvidedit.app.model.endOfVideoTrack
import com.fabvidedit.app.model.moveClipOnTimeline
import com.fabvidedit.app.model.rippleDeleteClip
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FabVidEditViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProjectStore(application)

    private val _projects = MutableStateFlow(store.load())
    val projects: StateFlow<List<VideoProject>> = _projects.asStateFlow()

    private val _activeProject = MutableStateFlow<VideoProject?>(null)
    val activeProject: StateFlow<VideoProject?> = _activeProject.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _busyMessage = MutableStateFlow<String?>(null)
    val busyMessage: StateFlow<String?> = _busyMessage.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val undoStack = ArrayDeque<VideoProject>()
    private val redoStack = ArrayDeque<VideoProject>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val exportManager = ExportManager(application, viewModelScope) { _exportState.value = it }

    fun createProject(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _busyMessage.value = "Préparation des médias…"
            try {
            // No destructive orphan sweep before a new import: startup/import overlap could
            // delete another in-flight source not yet referenced by the saved project.
            FabVidDiagnostics.mark("IMPORT_START")
            val imageUris = uris.filter(::isImageUri)
            val videoUris = uris.filterNot(::isImageUri)
            val imported = importContainers(videoUris)
            val additions = flattenImported(
                containers = imported,
                startingVideoTrack = 0,
                startingAudioTrack = 0,
                timelineStartMs = 0L,
            )
            val imageTrackStart = (additions.videoClips.maxOfOrNull(VideoClip::timelineTrackIndex) ?: -1) + 1
            val images = importImages(imageUris, imageTrackStart, 0L)
            val visualClips = additions.videoClips + images
            if (visualClips.isNotEmpty()) {
                val project = VideoProject(
                    name = "Projet ${SimpleDateFormat("dd MMM HH'h'mm", Locale.getDefault()).format(Date())}",
                    clips = visualClips,
                    sourceAudioTracks = additions.audioTracks,
                    timelineMode = TimelineMode.MULTITRACK,
                )
                Log.i(
                    TAG,
                    "Timeline tracks created visual=${visualClips.size} audio=${additions.audioTracks.size}",
                )
                _projects.value = listOf(project) + _projects.value
                store.save(_projects.value)
                openProject(project.id)
            }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                FabVidDiagnostics.logError("IMPORT_PROJECT", error)
                _userMessage.value = "Échec de l’import. Ouvre le Journal d’erreurs sur l’accueil."
            } finally {
                _busyMessage.value = null
            }
        }
    }

    fun addClips(uris: List<Uri>, timelineStartMs: Long = 0L) {
        val current = _activeProject.value ?: return
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _busyMessage.value = "Préparation des nouveaux médias…"
            try {
            // No destructive orphan sweep before a new import: startup/import overlap could
            // delete another in-flight source not yet referenced by the saved project.
            FabVidDiagnostics.mark("IMPORT_START")
            val project = current.asMultitrack()
            val imageUris = uris.filter(::isImageUri)
            val videoUris = uris.filterNot(::isImageUri)
            val startVideoTrack = (project.clips.maxOfOrNull(VideoClip::timelineTrackIndex) ?: -1) + 1
            val startAudioTrack = (project.sourceAudioTracks.maxOfOrNull(SourceAudioTrack::timelineTrackIndex) ?: -1) + 1
            val imported = importContainers(videoUris)
            val additions = flattenImported(
                containers = imported,
                startingVideoTrack = startVideoTrack,
                startingAudioTrack = startAudioTrack,
                timelineStartMs = timelineStartMs.coerceAtLeast(0L),
            )
            val imageTrackStart = (additions.videoClips.maxOfOrNull(VideoClip::timelineTrackIndex) ?: startVideoTrack - 1) + 1
            val images = importImages(imageUris, imageTrackStart, timelineStartMs.coerceAtLeast(0L))
            val visualAdditions = additions.videoClips + images
            if (visualAdditions.isNotEmpty()) {
                updateProject(
                    project.copy(
                        clips = project.clips + visualAdditions,
                        sourceAudioTracks = project.sourceAudioTracks + additions.audioTracks,
                        timelineMode = TimelineMode.MULTITRACK,
                    ),
                )
                Log.i(
                    TAG,
                    "Timeline tracks created visual=${visualAdditions.size} audio=${additions.audioTracks.size}",
                )
                _selectedClipId.value = visualAdditions.first().id
            }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                FabVidDiagnostics.logError("IMPORT_PROJECT", error)
                _userMessage.value = "Échec de l’import. Ouvre le Journal d’erreurs sur l’accueil."
            } finally {
                _busyMessage.value = null
            }
        }
    }

    fun setAudio(uri: Uri) {
        val project = _activeProject.value ?: return
        viewModelScope.launch {
            _busyMessage.value = "Analyse de l’audio…"
            persistReadPermission(uri)
            runCatching { MediaInspector.inspect(getApplication(), uri) }
                .onSuccess { asset ->
                    updateProject(
                        project.copy(
                            audioTrack = AudioTrack(
                                uri = asset.uri,
                                name = asset.name,
                                durationMs = asset.durationMs,
                            ),
                        ),
                    )
                }
                .onFailure { _userMessage.value = "Impossible d’importer cette piste audio" }
            _busyMessage.value = null
        }
    }

    fun openProject(id: String) {
        val project = _projects.value.firstOrNull { it.id == id } ?: return
        clearEditHistory()
        _activeProject.value = project
        FabVidDiagnostics.mark("OPEN_PROJECT clips=" + project.clips.size)
        _selectedClipId.value = project.clips.firstOrNull()?.id
        _exportState.value = ExportState.Idle
    }

    fun closeProject() {
        exportManager.cancel()
        clearEditHistory()
        _activeProject.value = null
        _selectedClipId.value = null
    }

    fun deleteProject(id: String) {
        _projects.value = _projects.value.filterNot { it.id == id }
        store.save(_projects.value)
        if (_activeProject.value?.id == id) closeProject()
        // Defer file reclamation until imports and exports hold explicit file leases.
    }

    fun renameProject(name: String) {
        val project = _activeProject.value ?: return
        val cleanName = name.trim().take(60)
        if (cleanName.isNotEmpty()) updateProject(project.copy(name = cleanName))
    }

    fun selectClip(id: String) {
        if (_activeProject.value?.clips?.any { it.id == id } == true) {
            _selectedClipId.value = id
        }
    }

    fun trimSelected(startMs: Long, endMs: Long) {
        val project = _activeProject.value ?: return
        val clipId = _selectedClipId.value ?: return
        trimVisualMedia(project, clipId, startMs, endMs)
    }

    fun trimClipOnTimeline(clipId: String, startMs: Long, endMs: Long, moveLeftEdge: Boolean) {
        val project = _activeProject.value ?: return
        if (project.clips.none { it.id == clipId }) return
        // moveLeftEdge is intentionally kept in the public signature because the timeline UI
        // supplies it, but grouped trimming derives both edge deltas from the requested range.
        @Suppress("UNUSED_VARIABLE")
        val edgeHint = moveLeftEdge
        trimVisualMedia(project, clipId, startMs, endMs)
    }

    private fun trimVisualMedia(project: VideoProject, clipId: String, startMs: Long, endMs: Long) {
        val anchor = project.clips.firstOrNull { it.id == clipId } ?: return
        val start = startMs.coerceIn(0L, anchor.durationMs - 1L)
        val end = endMs.coerceIn(start + 1L, anchor.durationMs)
        val requestedLeftOutputMs = ((start - anchor.trimStartMs) / anchor.speed.coerceAtLeast(0.01f)).toLong()
        val requestedRightOutputMs = ((anchor.trimEndMs - end) / anchor.speed.coerceAtLeast(0.01f)).toLong()
        val groupId = anchor.syncGroupId.takeIf { anchor.syncLocked }
        val visualIds = if (groupId == null) {
            setOf(anchor.id)
        } else {
            project.clips.filter { it.syncLocked && it.syncGroupId == groupId }.mapTo(mutableSetOf()) { it.id }
        }
        val audioIds = if (groupId == null) {
            emptySet()
        } else {
            project.sourceAudioTracks.filter { it.syncLocked && it.syncGroupId == groupId }.mapTo(mutableSetOf()) { it.id }
        }
        updateProject(
            applyLinkedTrim(
                project = project,
                visualIds = visualIds,
                audioIds = audioIds,
                requestedLeftOutputMs = requestedLeftOutputMs,
                requestedRightOutputMs = requestedRightOutputMs,
            ),
        )
        _selectedClipId.value = clipId
    }

    private fun applyLinkedTrim(
        project: VideoProject,
        visualIds: Set<String>,
        audioIds: Set<String>,
        requestedLeftOutputMs: Long,
        requestedRightOutputMs: Long,
    ): VideoProject {
        val visuals = project.clips.filter { it.id in visualIds }
        val audios = project.sourceAudioTracks.filter { it.id in audioIds }
        if (visuals.isEmpty() && audios.isEmpty()) return project

        val leftLowerBounds = buildList<Long> {
            visuals.forEach { clip ->
                add(maxOf(-(clip.trimStartMs / clip.speed.coerceAtLeast(0.01f)).toLong(), -clip.timelineStartMs))
            }
            audios.forEach { track -> add(maxOf(-track.trimStartMs, -track.timelineStartMs)) }
        }
        val leftUpperBounds = buildList<Long> {
            visuals.forEach { clip -> add((clip.outputDurationMs - MIN_CLIP_MS).coerceAtLeast(0L)) }
            audios.forEach { track -> add((track.outputDurationMs - MIN_CLIP_MS).coerceAtLeast(0L)) }
        }
        val leftLower = leftLowerBounds.maxOrNull() ?: 0L
        val leftUpper = leftUpperBounds.minOrNull() ?: 0L
        val leftDelta = requestedLeftOutputMs.coerceIn(leftLower, leftUpper.coerceAtLeast(leftLower))

        val rightLowerBounds = buildList<Long> {
            visuals.forEach { clip ->
                add(-((clip.durationMs - clip.trimEndMs) / clip.speed.coerceAtLeast(0.01f)).toLong())
            }
            audios.forEach { track -> add(-(track.durationMs - track.trimEndMs)) }
        }
        val rightUpperBounds = buildList<Long> {
            visuals.forEach { clip -> add((clip.outputDurationMs - leftDelta - MIN_CLIP_MS).coerceAtLeast(0L)) }
            audios.forEach { track -> add((track.outputDurationMs - leftDelta - MIN_CLIP_MS).coerceAtLeast(0L)) }
        }
        val rightLower = rightLowerBounds.maxOrNull() ?: 0L
        val rightUpper = rightUpperBounds.minOrNull() ?: 0L
        val rightDelta = requestedRightOutputMs.coerceIn(rightLower, rightUpper.coerceAtLeast(rightLower))

        val updatedClips = project.clips.map { clip ->
            if (clip.id !in visualIds) return@map clip
            val leftSourceDelta = (leftDelta * clip.speed).toLong()
            val rightSourceDelta = (rightDelta * clip.speed).toLong()
            val newStart = (clip.trimStartMs + leftSourceDelta).coerceIn(0L, clip.durationMs - 1L)
            val newEnd = (clip.trimEndMs - rightSourceDelta).coerceIn(newStart + 1L, clip.durationMs)
            val oldLocalAtNewStart = (newStart - clip.trimStartMs).coerceIn(0L, clip.sourceDurationMs)
            val shiftedKeyframes = clip.keyframes.mapNotNull { keyframe ->
                val absoluteSourceTime = clip.trimStartMs + keyframe.timeMs
                val newLocalTime = absoluteSourceTime - newStart
                keyframe.takeIf { newLocalTime in 0L..(newEnd - newStart) }?.copy(timeMs = newLocalTime)
            }
            clip.copy(
                trimStartMs = newStart,
                trimEndMs = newEnd,
                timelineStartMs = (clip.timelineStartMs + leftDelta).coerceAtLeast(0L),
                transform = if (newStart >= clip.trimStartMs) clip.transformAtSourceTime(oldLocalAtNewStart) else clip.transform,
                brightness = if (newStart >= clip.trimStartMs) clip.brightnessAtSourceTime(oldLocalAtNewStart) else clip.brightness,
                volume = if (newStart >= clip.trimStartMs) clip.volumeAtSourceTime(oldLocalAtNewStart) else clip.volume,
                keyframes = shiftedKeyframes,
            )
        }
        val updatedAudio = project.sourceAudioTracks.map { track ->
            if (track.id !in audioIds) return@map track
            val newStart = (track.trimStartMs + leftDelta).coerceIn(0L, track.durationMs - 1L)
            val newEnd = (track.trimEndMs - rightDelta).coerceIn(newStart + 1L, track.durationMs)
            track.copy(
                trimStartMs = newStart,
                trimEndMs = newEnd,
                timelineStartMs = (track.timelineStartMs + leftDelta).coerceAtLeast(0L),
                volume = if (newStart >= track.trimStartMs) track.volumeAtSourceTime(newStart) else track.volume,
                keyframes = track.keyframes.filter { it.timeMs in newStart..newEnd },
            )
        }
        return project.copy(clips = updatedClips, sourceAudioTracks = updatedAudio)
    }
    fun setSelectedSpeed(speed: Float) = updateSelectedClip { it.copy(speed = speed.coerceIn(0.25f, 4f)) }

    fun setSelectedVolume(projectPositionMs: Long, volume: Float) {
        val project = _activeProject.value ?: return
        val index = project.clips.indexOfFirst { it.id == _selectedClipId.value }
        if (index < 0) return
        val clip = project.clips[index]
        val normalized = volume.coerceIn(0f, 1f)
        val updatedClip = if (clip.keyframes.isEmpty()) {
            clip.copy(volume = normalized)
        } else {
            val sourceTime = project.sourceTimeForProjectPosition(index, projectPositionMs)
            clip.copy(
                keyframes = upsertKeyframe(
                    clip = clip,
                    sourceTimeMs = sourceTime,
                    transform = clip.transformAtSourceTime(sourceTime),
                    brightness = clip.brightnessAtSourceTime(sourceTime),
                    volume = normalized,
                ),
            )
        }
        replaceClip(project, index, updatedClip)
    }

    fun rotateSelected() = updateSelectedClip {
        it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360)
    }

    fun setSelectedFilter(filter: ClipFilter) = updateSelectedClip { it.copy(filter = filter) }

    fun duplicateSelected() {
        val project = _activeProject.value ?: return
        val index = project.clips.indexOfFirst { it.id == _selectedClipId.value }
        if (index < 0) return
        val source = project.clips[index]
        val duplicate = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} copie",
            timelineTrackIndex = if (project.timelineMode == TimelineMode.MULTITRACK) {
                (project.clips.maxOfOrNull(VideoClip::timelineTrackIndex) ?: -1) + 1
            } else {
                source.timelineTrackIndex
            },
        )
        val clips = project.clips.toMutableList().apply { add(index + 1, duplicate) }
        updateProject(project.copy(clips = clips))
        _selectedClipId.value = duplicate.id
    }

    fun moveSelected(direction: Int) {
        val project = _activeProject.value ?: return
        val from = project.clips.indexOfFirst { it.id == _selectedClipId.value }
        val to = (from + direction).coerceIn(0, project.clips.lastIndex)
        if (from < 0 || from == to) return
        val clips = project.clips.toMutableList()
        val clip = clips.removeAt(from)
        clips.add(to, clip)
        updateProject(project.copy(clips = clips))
    }

    fun removeSelectedClip() {
        val project = _activeProject.value ?: return
        val selectedId = _selectedClipId.value ?: return
        val removed = project.clips.firstOrNull { it.id == selectedId } ?: return
        val linkedGroupId = removed.syncGroupId.takeIf { removed.syncLocked }
        val visualUpdate = project.rippleDeleteClip(selectedId)
        val updated = if (linkedGroupId == null) {
            visualUpdate
        } else {
            visualUpdate.copy(
                sourceAudioTracks = visualUpdate.sourceAudioTracks.filterNot { track ->
                    track.syncLocked && track.syncGroupId == linkedGroupId
                },
            )
        }
        updateProject(updated)
        _selectedClipId.value = updated.clips.minByOrNull {
            kotlin.math.abs(it.timelineStartMs - removed.timelineStartMs)
        }?.id
    }

    /** Direct and accessible alternative to drag/drop; keeps linked A/V moving together. */
    fun appendSelectedToMainTrack() {
        val project = _activeProject.value ?: return
        val id = _selectedClipId.value ?: return
        val clip = project.clips.firstOrNull { it.id == id } ?: return
        val endOfV1 = project.endOfVideoTrack(trackIndex = 0, excludedClipId = clip.id)
        moveClip(id, targetTrackIndex = 0, targetStartMs = endOfV1)
    }

    fun moveClip(clipId: String, targetTrackIndex: Int, targetStartMs: Long) {
        val project = _activeProject.value ?: return
        val anchor = project.clips.firstOrNull { it.id == clipId } ?: return
        val groupId = anchor.syncGroupId.takeIf { anchor.syncLocked }
        if (groupId == null) {
            updateProject(project.moveClipOnTimeline(clipId, targetTrackIndex, targetStartMs))
            _selectedClipId.value = clipId
            return
        }
        val groupStarts = project.clips.filter { it.syncLocked && it.syncGroupId == groupId }.map { it.timelineStartMs } +
            project.sourceAudioTracks.filter { it.syncLocked && it.syncGroupId == groupId }.map { it.timelineStartMs }
        val minimumStart = groupStarts.minOrNull() ?: anchor.timelineStartMs
        val requestedDelta = targetStartMs.coerceAtLeast(0L) - anchor.timelineStartMs
        val safeDelta = requestedDelta.coerceAtLeast(-minimumStart)
        val movedAnchorProject = project.moveClipOnTimeline(
            clipId,
            targetTrackIndex,
            (anchor.timelineStartMs + safeDelta).coerceAtLeast(0L),
        )
        val movedAnchor = movedAnchorProject.clips.first { it.id == clipId }
        val actualDelta = movedAnchor.timelineStartMs - anchor.timelineStartMs
        val moved = movedAnchorProject.copy(
            clips = movedAnchorProject.clips.map { clip ->
                if (clip.id != clipId && clip.syncLocked && clip.syncGroupId == groupId) {
                    clip.copy(timelineStartMs = (clip.timelineStartMs + actualDelta).coerceAtLeast(0L))
                } else clip
            },
            sourceAudioTracks = movedAnchorProject.sourceAudioTracks.map { track ->
                if (track.syncLocked && track.syncGroupId == groupId) {
                    track.copy(timelineStartMs = (track.timelineStartMs + actualDelta).coerceAtLeast(0L))
                } else track
            },
        )
        updateProject(moved)
        _selectedClipId.value = clipId
    }

    /** Exchange entire visual lanes. This changes depth, never any clip or audio timestamp. */
    fun changeSelectedLayer(direction: Int) {
        val project = _activeProject.value ?: return
        val id = _selectedClipId.value ?: return
        val reordered = VideoLayerPolicy.moveLayer(project, id, direction)
        if (reordered != project) updateProject(reordered)
    }

    fun setSelectedOpacity(value: Float) =
        updateSelectedClip { it.copy(opacity = value.coerceIn(0f, 1f)) }

    fun setClipSyncLocked(clipId: String, locked: Boolean) {
        val project = _activeProject.value ?: return
        val clip = project.clips.firstOrNull { it.id == clipId } ?: return
        val groupId = clip.syncGroupId ?: "link:${UUID.randomUUID()}"
        updateProject(project.copy(clips = project.clips.map {
            if (it.id == clipId) it.copy(syncGroupId = groupId, syncLocked = locked) else it
        }))
    }

    fun setSourceAudioSyncLocked(trackId: String, locked: Boolean) {
        val project = _activeProject.value ?: return
        val track = project.sourceAudioTracks.firstOrNull { it.id == trackId } ?: return
        val groupId = track.syncGroupId ?: "link:${UUID.randomUUID()}"
        updateProject(project.copy(sourceAudioTracks = project.sourceAudioTracks.map {
            if (it.id == trackId) it.copy(syncGroupId = groupId, syncLocked = locked) else it
        }))
    }

    fun linkClipToClip(anchorId: String, targetId: String) {
        if (anchorId == targetId) return
        val project = _activeProject.value ?: return
        val anchor = project.clips.firstOrNull { it.id == anchorId } ?: return
        val target = project.clips.firstOrNull { it.id == targetId } ?: return
        val groupId = anchor.syncGroupId ?: target.syncGroupId ?: "link:${UUID.randomUUID()}"
        val mergeGroups = setOfNotNull(anchor.syncGroupId, target.syncGroupId)
        updateProject(project.copy(
            clips = project.clips.map { clip ->
                if (clip.id == anchorId || clip.id == targetId || clip.syncGroupId in mergeGroups) {
                    clip.copy(syncGroupId = groupId, syncLocked = true)
                } else clip
            },
            sourceAudioTracks = project.sourceAudioTracks.map { track ->
                if (track.syncGroupId in mergeGroups) track.copy(syncGroupId = groupId, syncLocked = true) else track
            },
        ))
    }

    fun linkClipToSourceAudio(anchorId: String, targetTrackId: String) {
        val project = _activeProject.value ?: return
        val anchor = project.clips.firstOrNull { it.id == anchorId } ?: return
        val target = project.sourceAudioTracks.firstOrNull { it.id == targetTrackId } ?: return
        val groupId = anchor.syncGroupId ?: target.syncGroupId ?: "link:${UUID.randomUUID()}"
        val mergeGroups = setOfNotNull(anchor.syncGroupId, target.syncGroupId)
        updateProject(project.copy(
            clips = project.clips.map { clip ->
                if (clip.id == anchorId || clip.syncGroupId in mergeGroups) clip.copy(syncGroupId = groupId, syncLocked = true) else clip
            },
            sourceAudioTracks = project.sourceAudioTracks.map { track ->
                if (track.id == targetTrackId || track.syncGroupId in mergeGroups) track.copy(syncGroupId = groupId, syncLocked = true) else track
            },
        ))
    }

    fun splitSelectedAt(projectPositionMs: Long) {
        val project = _activeProject.value ?: return
        val selectedId = _selectedClipId.value ?: return
        val anchor = project.clips.firstOrNull { it.id == selectedId } ?: return
        val anchorStart = anchor.timelineStartMs
        val anchorEnd = anchor.timelineStartMs + anchor.outputDurationMs
        if (projectPositionMs <= anchorStart + MIN_CLIP_MS || projectPositionMs >= anchorEnd - MIN_CLIP_MS) {
            _userMessage.value = "Place la tête de lecture à l’intérieur du clip sélectionné"
            return
        }

        val sourceGroupId = anchor.syncGroupId.takeIf { anchor.syncLocked }
        if (sourceGroupId == null) {
            val localOutputMs = projectPositionMs - anchorStart
            val splitLocalSourceMs = (localOutputMs * anchor.speed).toLong().coerceIn(1L, anchor.sourceDurationMs - 1L)
            val splitSourceMs = anchor.trimStartMs + splitLocalSourceMs
            val left = anchor.copy(
                trimEndMs = splitSourceMs,
                keyframes = anchor.keyframes.filter { it.timeMs <= splitLocalSourceMs },
            )
            val right = anchor.copy(
                id = UUID.randomUUID().toString(),
                trimStartMs = splitSourceMs,
                timelineStartMs = anchor.timelineStartMs + left.outputDurationMs,
                keyframes = anchor.keyframes.filter { it.timeMs > splitLocalSourceMs }
                    .map { it.copy(timeMs = it.timeMs - splitLocalSourceMs) },
                transform = anchor.transformAtSourceTime(splitLocalSourceMs),
                brightness = anchor.brightnessAtSourceTime(splitLocalSourceMs),
                volume = anchor.volumeAtSourceTime(splitLocalSourceMs),
            )
            val index = project.clips.indexOfFirst { it.id == selectedId }
            val clips = project.clips.toMutableList().apply {
                set(index, left)
                add(index + 1, right)
            }
            updateProject(project.copy(clips = clips))
            _selectedClipId.value = right.id
            return
        }

        val leftGroupId = "split:${UUID.randomUUID()}:L"
        val rightGroupId = "split:${UUID.randomUUID()}:R"
        var selectedRightId: String? = null
        val rebuiltClips = buildList {
            project.clips.forEach { clip ->
                if (!clip.syncLocked || clip.syncGroupId != sourceGroupId) {
                    add(clip)
                    return@forEach
                }
                val start = clip.timelineStartMs
                val end = start + clip.outputDurationMs
                when {
                    projectPositionMs <= start + MIN_CLIP_MS -> {
                        add(clip.copy(syncGroupId = rightGroupId, syncLocked = true))
                    }
                    projectPositionMs >= end - MIN_CLIP_MS -> {
                        add(clip.copy(syncGroupId = leftGroupId, syncLocked = true))
                    }
                    else -> {
                        val localOutputMs = projectPositionMs - start
                        val splitLocalSourceMs = (localOutputMs * clip.speed).toLong()
                            .coerceIn(1L, clip.sourceDurationMs - 1L)
                        val splitSourceMs = clip.trimStartMs + splitLocalSourceMs
                        val left = clip.copy(
                            syncGroupId = leftGroupId,
                            syncLocked = true,
                            trimEndMs = splitSourceMs,
                            keyframes = clip.keyframes.filter { it.timeMs <= splitLocalSourceMs },
                        )
                        val right = clip.copy(
                            id = UUID.randomUUID().toString(),
                            syncGroupId = rightGroupId,
                            syncLocked = true,
                            trimStartMs = splitSourceMs,
                            timelineStartMs = clip.timelineStartMs + left.outputDurationMs,
                            keyframes = clip.keyframes.filter { it.timeMs > splitLocalSourceMs }
                                .map { it.copy(timeMs = it.timeMs - splitLocalSourceMs) },
                            transform = clip.transformAtSourceTime(splitLocalSourceMs),
                            brightness = clip.brightnessAtSourceTime(splitLocalSourceMs),
                            volume = clip.volumeAtSourceTime(splitLocalSourceMs),
                        )
                        add(left)
                        add(right)
                        if (clip.id == selectedId) selectedRightId = right.id
                    }
                }
            }
        }

        val rebuiltAudio = buildList {
            project.sourceAudioTracks.forEach { track ->
                if (!track.syncLocked || track.syncGroupId != sourceGroupId) {
                    add(track)
                    return@forEach
                }
                val start = track.timelineStartMs
                val end = start + track.outputDurationMs
                when {
                    projectPositionMs <= start + MIN_CLIP_MS -> {
                        add(track.copy(syncGroupId = rightGroupId, syncLocked = true))
                    }
                    projectPositionMs >= end - MIN_CLIP_MS -> {
                        add(track.copy(syncGroupId = leftGroupId, syncLocked = true))
                    }
                    else -> {
                        val splitSourceMs = (track.trimStartMs + projectPositionMs - start)
                            .coerceIn(track.trimStartMs + 1L, track.trimEndMs - 1L)
                        val left = track.copy(
                            syncGroupId = leftGroupId,
                            syncLocked = true,
                            trimEndMs = splitSourceMs,
                            keyframes = track.keyframes.filter { it.timeMs <= splitSourceMs },
                        )
                        val right = track.copy(
                            id = UUID.randomUUID().toString(),
                            syncGroupId = rightGroupId,
                            syncLocked = true,
                            trimStartMs = splitSourceMs,
                            timelineStartMs = track.timelineStartMs + left.outputDurationMs,
                            volume = track.volumeAtSourceTime(splitSourceMs),
                            keyframes = track.keyframes.filter { it.timeMs > splitSourceMs },
                        )
                        add(left)
                        add(right)
                    }
                }
            }
        }

        updateProject(project.copy(clips = rebuiltClips, sourceAudioTracks = rebuiltAudio))
        _selectedClipId.value = selectedRightId
            ?: rebuiltClips.firstOrNull { it.syncGroupId == rightGroupId }?.id
            ?: selectedId
        _userMessage.value = "Groupe scindé : gauche et droite sont maintenant deux groupes indépendants"
    }
    fun setAspectRatio(preset: AspectRatioPreset) {
        val project = _activeProject.value ?: return
        updateProject(project.copy(aspectRatio = preset))
    }

    fun setSelectedTransform(projectPositionMs: Long, transform: ClipTransform) {
        val project = _activeProject.value ?: return
        val index = project.clips.indexOfFirst { it.id == _selectedClipId.value }
        if (index < 0) return
        val clip = project.clips[index]
        val normalized = transform.normalized()
        val updatedClip = if (clip.keyframes.isEmpty()) {
            clip.copy(transform = normalized)
        } else {
            val sourceTime = project.sourceTimeForProjectPosition(index, projectPositionMs)
            clip.copy(
                keyframes = upsertKeyframe(
                    clip = clip,
                    sourceTimeMs = sourceTime,
                    transform = normalized,
                    brightness = clip.brightnessAtSourceTime(sourceTime),
                    volume = clip.volumeAtSourceTime(sourceTime),
                ),
            )
        }
        replaceClip(project, index, updatedClip)
    }

    fun setSelectedBrightness(projectPositionMs: Long, brightness: Float) {
        val project = _activeProject.value ?: return
        val index = project.clips.indexOfFirst { it.id == _selectedClipId.value }
        if (index < 0) return
        val clip = project.clips[index]
        val normalized = brightness.coerceIn(0f, 2f)
        val updatedClip = if (clip.keyframes.isEmpty()) {
            clip.copy(brightness = normalized)
        } else {
            val sourceTime = project.sourceTimeForProjectPosition(index, projectPositionMs)
            clip.copy(
                keyframes = upsertKeyframe(
                    clip = clip,
                    sourceTimeMs = sourceTime,
                    transform = clip.transformAtSourceTime(sourceTime),
                    brightness = normalized,
                    volume = clip.volumeAtSourceTime(sourceTime),
                ),
            )
        }
        replaceClip(project, index, updatedClip)
    }

    fun addSelectedKeyframe(projectPositionMs: Long) {
        val project = _activeProject.value ?: return
        val index = project.clips.indexOfFirst { it.id == _selectedClipId.value }
        if (index < 0) return
        val clip = project.clips[index]
        val sourceTime = project.sourceTimeForProjectPosition(index, projectPositionMs)
        val currentTransform = clip.transformAtSourceTime(sourceTime)
        replaceClip(
            project,
            index,
            clip.copy(
                keyframes = upsertKeyframe(
                    clip = clip,
                    sourceTimeMs = sourceTime,
                    transform = currentTransform,
                    brightness = clip.brightnessAtSourceTime(sourceTime),
                    volume = clip.volumeAtSourceTime(sourceTime),
                ),
            ),
        )
    }

    fun removeSelectedKeyframe(projectPositionMs: Long) {
        val project = _activeProject.value ?: return
        val index = project.clips.indexOfFirst { it.id == _selectedClipId.value }
        if (index < 0) return
        val clip = project.clips[index]
        val sourceTime = project.sourceTimeForProjectPosition(index, projectPositionMs)
        val nearest = nearestKeyframe(clip, sourceTime) ?: return
        replaceClip(project, index, clip.copy(keyframes = clip.keyframes.filterNot { it.id == nearest.id }))
    }

    fun setSelectedKeyframeEasing(projectPositionMs: Long, easing: MotionEasing) {
        val project = _activeProject.value ?: return
        val index = project.clips.indexOfFirst { it.id == _selectedClipId.value }
        if (index < 0) return
        val clip = project.clips[index]
        val sourceTime = project.sourceTimeForProjectPosition(index, projectPositionMs)
        val nearest = nearestKeyframe(clip, sourceTime) ?: return
        replaceClip(
            project,
            index,
            clip.copy(keyframes = clip.keyframes.map { if (it.id == nearest.id) it.copy(easing = easing) else it }),
        )
    }

    fun resetSelectedTransform() = updateSelectedClip {
        it.copy(transform = ClipTransform(), brightness = 1f, keyframes = emptyList())
    }

    fun setTransitionAfterSelected(type: TransitionType) {
        val project = _activeProject.value ?: return
        val index = project.clips.indexOfFirst { it.id == _selectedClipId.value }
        val from = project.clips.getOrNull(index) ?: return
        val to = project.nextClipOnTrack(index)
        if (to == null) {
            _userMessage.value = "Sélectionne un clip qui possède un clip suivant sur la même piste"
            return
        }
        val existing = project.transitionAfter(index)
        val replacement = (existing ?: ClipTransition(fromClipId = from.id, toClipId = to.id))
            .copy(type = type)
        updateProject(
            project.copy(
                transitions = project.transitions.filterNot {
                    it.fromClipId == from.id && it.toClipId == to.id
                } + replacement,
            ),
        )
    }

    fun setTransitionDurationAfterSelected(durationMs: Long) {
        val project = _activeProject.value ?: return
        val index = project.clips.indexOfFirst { it.id == _selectedClipId.value }
        val existing = project.transitionAfter(index) ?: return
        updateProject(
            project.copy(
                transitions = project.transitions.map {
                    if (it.id == existing.id) it.copy(durationMs = durationMs.coerceIn(200, 2_000)) else it
                },
            ),
        )
    }

    fun removeTransitionAfterSelected() {
        val project = _activeProject.value ?: return
        val index = project.clips.indexOfFirst { it.id == _selectedClipId.value }
        val existing = project.transitionAfter(index) ?: return
        updateProject(project.copy(transitions = project.transitions.filterNot { it.id == existing.id }))
    }

    fun applyAutomaticTransitions() {
        val project = _activeProject.value ?: return
        val pairs = if (project.timelineMode == TimelineMode.MULTITRACK) {
            project.clips.groupBy(VideoClip::timelineTrackIndex).values.flatMap { track ->
                track.sortedBy(VideoClip::timelineStartMs).zipWithNext()
            }
        } else {
            project.clips.zipWithNext()
        }
        if (pairs.isEmpty()) {
            _userMessage.value = "Ajoute au moins deux clips successifs sur une même piste"
            return
        }
        val cycle = listOf(
            TransitionType.FADE,
            TransitionType.SLIDE_LEFT,
            TransitionType.ZOOM,
            TransitionType.SLIDE_RIGHT,
            TransitionType.ROTATE,
        )
        val transitions = pairs.mapIndexed { index, (from, to) ->
            ClipTransition(
                fromClipId = from.id,
                toClipId = to.id,
                type = cycle[index % cycle.size],
                durationMs = 650,
            )
        }
        updateProject(project.copy(transitions = transitions))
    }

    fun clearTransitions() {
        val project = _activeProject.value ?: return
        updateProject(project.copy(transitions = emptyList()))
    }

    fun addText(layer: TextLayer) {
        val project = _activeProject.value ?: return
        updateProject(project.copy(textLayers = project.textLayers + layer))
    }

    fun updateText(layer: TextLayer) {
        val project = _activeProject.value ?: return
        updateProject(
            project.copy(textLayers = project.textLayers.map { if (it.id == layer.id) layer else it }),
        )
    }

    fun removeText(id: String) {
        val project = _activeProject.value ?: return
        updateProject(project.copy(textLayers = project.textLayers.filterNot { it.id == id }))
    }

    fun setAudioVolume(projectPositionMs: Long, volume: Float) {
        val project = _activeProject.value ?: return
        val track = project.audioTrack ?: return
        val normalized = volume.coerceIn(0f, 1f)
        val updatedTrack = if (track.keyframes.isEmpty()) {
            track.copy(volume = normalized)
        } else {
            track.copy(keyframes = upsertAudioKeyframe(track, projectPositionMs, normalized))
        }
        updateProject(project.copy(audioTrack = updatedTrack))
    }

    fun addAudioKeyframe(projectPositionMs: Long) {
        val project = _activeProject.value ?: return
        val track = project.audioTrack ?: return
        updateProject(
            project.copy(
                audioTrack = track.copy(
                    keyframes = upsertAudioKeyframe(
                        track = track,
                        projectPositionMs = projectPositionMs,
                        volume = track.volumeAtProjectTime(projectPositionMs),
                    ),
                ),
            ),
        )
    }

    fun removeAudioKeyframe(projectPositionMs: Long) {
        val project = _activeProject.value ?: return
        val track = project.audioTrack ?: return
        val nearest = nearestAudioKeyframe(track, projectPositionMs) ?: return
        updateProject(
            project.copy(audioTrack = track.copy(keyframes = track.keyframes.filterNot { it.id == nearest.id })),
        )
    }

    fun setAudioKeyframeEasing(projectPositionMs: Long, easing: MotionEasing) {
        val project = _activeProject.value ?: return
        val track = project.audioTrack ?: return
        val nearest = nearestAudioKeyframe(track, projectPositionMs) ?: return
        updateProject(
            project.copy(
                audioTrack = track.copy(
                    keyframes = track.keyframes.map {
                        if (it.id == nearest.id) it.copy(easing = easing) else it
                    },
                ),
            ),
        )
    }

    fun removeAudio() {
        val project = _activeProject.value ?: return
        updateProject(project.copy(audioTrack = null))
    }

    fun clearClipSelection() {
        _selectedClipId.value = null
    }

    fun moveSourceAudioTrack(id: String, targetTrackIndex: Int, targetStartMs: Long) {
        val project = _activeProject.value ?: return
        val anchor = project.sourceAudioTracks.firstOrNull { it.id == id } ?: return
        val groupId = anchor.syncGroupId.takeIf { anchor.syncLocked }
        if (groupId == null) {
            updateProject(project.copy(sourceAudioTracks = project.sourceAudioTracks.map { track ->
                if (track.id == id) track.copy(
                    timelineTrackIndex = targetTrackIndex.coerceAtLeast(0),
                    timelineStartMs = targetStartMs.coerceAtLeast(0L),
                ) else track
            }))
            return
        }
        val groupStarts = project.clips.filter { it.syncLocked && it.syncGroupId == groupId }.map { it.timelineStartMs } +
            project.sourceAudioTracks.filter { it.syncLocked && it.syncGroupId == groupId }.map { it.timelineStartMs }
        val minimumStart = groupStarts.minOrNull() ?: anchor.timelineStartMs
        val requestedDelta = targetStartMs.coerceAtLeast(0L) - anchor.timelineStartMs
        val delta = requestedDelta.coerceAtLeast(-minimumStart)
        updateProject(project.copy(
            clips = project.clips.map { clip ->
                if (clip.syncLocked && clip.syncGroupId == groupId) clip.copy(timelineStartMs = clip.timelineStartMs + delta) else clip
            },
            sourceAudioTracks = project.sourceAudioTracks.map { track ->
                when {
                    track.id == id -> track.copy(
                        timelineTrackIndex = targetTrackIndex.coerceAtLeast(0),
                        timelineStartMs = track.timelineStartMs + delta,
                    )
                    track.syncLocked && track.syncGroupId == groupId -> track.copy(timelineStartMs = track.timelineStartMs + delta)
                    else -> track
                }
            },
        ))
    }

    fun trimSourceAudioTrack(id: String, startMs: Long, endMs: Long, moveLeftEdge: Boolean) {
        val project = _activeProject.value ?: return
        val anchor = project.sourceAudioTracks.firstOrNull { it.id == id } ?: return
        val start = startMs.coerceIn(0L, anchor.durationMs - 1L)
        val end = endMs.coerceIn(start + 1L, anchor.durationMs)
        @Suppress("UNUSED_VARIABLE")
        val edgeHint = moveLeftEdge
        val requestedLeftOutputMs = start - anchor.trimStartMs
        val requestedRightOutputMs = anchor.trimEndMs - end
        val groupId = anchor.syncGroupId.takeIf { anchor.syncLocked }
        val visualIds = if (groupId == null) {
            emptySet()
        } else {
            project.clips.filter { it.syncLocked && it.syncGroupId == groupId }.mapTo(mutableSetOf()) { it.id }
        }
        val audioIds = if (groupId == null) {
            setOf(anchor.id)
        } else {
            project.sourceAudioTracks.filter { it.syncLocked && it.syncGroupId == groupId }.mapTo(mutableSetOf()) { it.id }
        }
        updateProject(
            applyLinkedTrim(
                project = project,
                visualIds = visualIds,
                audioIds = audioIds,
                requestedLeftOutputMs = requestedLeftOutputMs,
                requestedRightOutputMs = requestedRightOutputMs,
            ),
        )
    }
    fun duplicateSourceAudioTrack(id: String) {
        val project = _activeProject.value ?: return
        val source = project.sourceAudioTracks.firstOrNull { it.id == id } ?: return
        val newTrack = (project.sourceAudioTracks.maxOfOrNull(SourceAudioTrack::timelineTrackIndex) ?: -1) + 1
        val duplicate = source.copy(
            id = UUID.randomUUID().toString(),
            timelineTrackIndex = newTrack,
        )
        updateProject(project.copy(sourceAudioTracks = project.sourceAudioTracks + duplicate))
    }

    fun removeSourceAudioTrack(id: String) {
        val project = _activeProject.value ?: return
        updateProject(project.copy(sourceAudioTracks = project.sourceAudioTracks.filterNot { it.id == id }))
    }

    fun setSourceAudioTrackVolume(id: String, projectPositionMs: Long, volume: Float) {
        val project = _activeProject.value ?: return
        val source = project.sourceAudioTracks.firstOrNull { it.id == id } ?: return
        val normalized = volume.coerceIn(0f, 1f)
        val sourceTime = sourceTimeForProjectPosition(source, projectPositionMs)
        val updated = if (source.keyframes.isEmpty()) {
            source.copy(volume = normalized)
        } else {
            source.copy(keyframes = upsertSourceAudioKeyframe(source, sourceTime, normalized))
        }
        updateProject(
            project.copy(sourceAudioTracks = project.sourceAudioTracks.map { if (it.id == id) updated else it }),
        )
    }

    fun addSourceAudioKeyframe(id: String, projectPositionMs: Long) {
        val project = _activeProject.value ?: return
        val source = project.sourceAudioTracks.firstOrNull { it.id == id } ?: return
        val sourceTime = sourceTimeForProjectPosition(source, projectPositionMs)
        val volume = source.volumeAtSourceTime(sourceTime)
        val updated = source.copy(keyframes = upsertSourceAudioKeyframe(source, sourceTime, volume))
        updateProject(project.copy(sourceAudioTracks = project.sourceAudioTracks.map { if (it.id == id) updated else it }))
    }

    fun removeSourceAudioKeyframe(id: String, projectPositionMs: Long) {
        val project = _activeProject.value ?: return
        val source = project.sourceAudioTracks.firstOrNull { it.id == id } ?: return
        val sourceTime = sourceTimeForProjectPosition(source, projectPositionMs)
        val nearest = nearestSourceAudioKeyframe(source, sourceTime) ?: return
        val updated = source.copy(keyframes = source.keyframes.filterNot { it.id == nearest.id })
        updateProject(project.copy(sourceAudioTracks = project.sourceAudioTracks.map { if (it.id == id) updated else it }))
    }

    fun clearSourceAudioKeyframes(id: String) {
        val project = _activeProject.value ?: return
        updateProject(
            project.copy(
                sourceAudioTracks = project.sourceAudioTracks.map {
                    if (it.id == id) it.copy(keyframes = emptyList()) else it
                },
            ),
        )
    }

    fun applySourceAudioFadeIn(id: String, requestedDurationMs: Long = 1_000L) {
        val project = _activeProject.value ?: return
        val source = project.sourceAudioTracks.firstOrNull { it.id == id } ?: return
        val duration = requestedDurationMs.coerceIn(120L, (source.outputDurationMs / 2L).coerceAtLeast(120L))
        val end = (source.trimStartMs + duration).coerceAtMost(source.trimEndMs)
        val kept = source.keyframes.filterNot { it.timeMs in source.trimStartMs..end }
        val updated = source.copy(
            keyframes = (kept + listOf(
                SourceAudioKeyframe(timeMs = source.trimStartMs, volume = 0f),
                SourceAudioKeyframe(timeMs = end, volume = source.volume.coerceIn(0f, 1f)),
            )).sortedBy(SourceAudioKeyframe::timeMs),
        )
        updateProject(project.copy(sourceAudioTracks = project.sourceAudioTracks.map { if (it.id == id) updated else it }))
    }

    fun applySourceAudioFadeOut(id: String, requestedDurationMs: Long = 1_000L) {
        val project = _activeProject.value ?: return
        val source = project.sourceAudioTracks.firstOrNull { it.id == id } ?: return
        val duration = requestedDurationMs.coerceIn(120L, (source.outputDurationMs / 2L).coerceAtLeast(120L))
        val start = (source.trimEndMs - duration).coerceAtLeast(source.trimStartMs)
        val kept = source.keyframes.filterNot { it.timeMs in start..source.trimEndMs }
        val updated = source.copy(
            keyframes = (kept + listOf(
                SourceAudioKeyframe(timeMs = start, volume = source.volume.coerceIn(0f, 1f)),
                SourceAudioKeyframe(timeMs = source.trimEndMs, volume = 0f),
            )).sortedBy(SourceAudioKeyframe::timeMs),
        )
        updateProject(project.copy(sourceAudioTracks = project.sourceAudioTracks.map { if (it.id == id) updated else it }))
    }

    private fun sourceTimeForProjectPosition(track: SourceAudioTrack, projectPositionMs: Long): Long =
        (track.trimStartMs + projectPositionMs - track.timelineStartMs)
            .coerceIn(track.trimStartMs, track.trimEndMs)

    private fun upsertSourceAudioKeyframe(
        track: SourceAudioTrack,
        sourceTimeMs: Long,
        volume: Float,
    ): List<SourceAudioKeyframe> {
        val time = sourceTimeMs.coerceIn(track.trimStartMs, track.trimEndMs)
        val existing = nearestSourceAudioKeyframe(track, time)
        val updated = if (existing == null) {
            track.keyframes + SourceAudioKeyframe(timeMs = time, volume = volume.coerceIn(0f, 1f))
        } else {
            track.keyframes.map { if (it.id == existing.id) it.copy(timeMs = time, volume = volume.coerceIn(0f, 1f)) else it }
        }
        return updated.sortedBy(SourceAudioKeyframe::timeMs)
    }

    private fun nearestSourceAudioKeyframe(track: SourceAudioTrack, sourceTimeMs: Long): SourceAudioKeyframe? =
        track.keyframes.minByOrNull { kotlin.math.abs(it.timeMs - sourceTimeMs) }
            ?.takeIf { kotlin.math.abs(it.timeMs - sourceTimeMs) <= KEYFRAME_SNAP_OUTPUT_MS }

    fun startExport(settings: ExportSettings) {
        val project = _activeProject.value ?: return
        if (project.clips.isEmpty()) {
            _userMessage.value = "Ajoute au moins une vidéo avant l’export"
            return
        }
        exportManager.start(project, settings)
    }

    /** Compatibility for legacy screens still passing a short-side resolution. */
    fun startExport(resolutionShortSide: Int) {
        val resolution = when (resolutionShortSide) {
            720 -> ExportResolution.P720
            1440 -> ExportResolution.P1440
            2160 -> ExportResolution.P2160
            else -> ExportResolution.P1080
        }
        startExport(ExportSettings(resolution = resolution))
    }

    fun cancelExport() = exportManager.cancel()

    fun clearExportResult() {
        _exportState.value = ExportState.Idle
    }

    fun consumeMessage() {
        _userMessage.value = null
    }

    private suspend fun importContainers(uris: List<Uri>): List<ImportedMediaContainer> =
        uris.mapNotNull { uri ->
            persistReadPermission(uri)
            runCatching {
                MediaImportPipeline.importContainer(getApplication(), uri) { stage ->
                    _busyMessage.value = stage
                    FabVidDiagnostics.mark(stage)
                }
            }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    if (error is OutOfMemoryError) throw error
                    FabVidDiagnostics.logError("IMPORT_CONTAINER", error)
                    Log.w(TAG, "Import rejected", error)
                    _userMessage.value = error.localizedMessage ?: "Une vidéo n’a pas pu être importée"
                }
                .getOrNull()
        }

    private fun flattenImported(
        containers: List<ImportedMediaContainer>,
        startingVideoTrack: Int,
        startingAudioTrack: Int,
        timelineStartMs: Long,
    ): ImportedAdditions {
        var videoTrack = startingVideoTrack
        var audioTrack = startingAudioTrack
        val clips = mutableListOf<VideoClip>()
        val audio = mutableListOf<SourceAudioTrack>()

        containers.forEach { container ->
            val syncGroup = "source:${container.id}"
            Log.i(TAG, "MULTITRACK MAP: container=${container.id} uri=${container.originalUri} syncGroup=$syncGroup")
            val videoLaneByResolution = mutableMapOf<Triple<Int, Int, Int>, Int>()
            container.videoSources.forEach { source ->
                val laneKey = Triple(source.streamIndex, source.width, source.height)
                val track = videoLaneByResolution.getOrPut(laneKey) { videoTrack++ }
                clips += VideoClip(
                    sourceId = source.id,
                    containerUri = source.containerUri,
                    sourceStreamIndex = source.streamIndex,
                    syncGroupId = syncGroup,
                    syncLocked = true,
                    timelineTrackIndex = track,
                    timelineStartMs = timelineStartMs + source.timelineOffsetMs,
                    uri = source.uri,
                    name = source.name,
                    durationMs = source.durationMs,
                    width = source.width,
                    height = source.height,
                    rotationDegrees = source.rotationDegrees,
                    volume = 0f,
                )
                Log.i(
                    TAG,
                    "MULTITRACK MAP: container=${container.id} VIDEO ${source.streamIndex} ${source.width}x${source.height} -> V${track + 1} " +
                        "source=${source.id} segment=${source.resolutionSegmentIndex} " +
                        "start=${timelineStartMs + source.timelineOffsetMs} duration=${source.durationMs}",
                )
            }
            container.audioSources.forEach { source ->
                val track = audioTrack++
                audio += SourceAudioTrack(
                    sourceId = source.id,
                    containerUri = source.containerUri,
                    sourceStreamIndex = source.streamIndex,
                    syncGroupId = syncGroup,
                    syncLocked = true,
                    timelineTrackIndex = track,
                    timelineStartMs = timelineStartMs + source.timelineOffsetMs,
                    uri = source.uri,
                    name = source.name,
                    durationMs = source.durationMs,
                    volume = 1f,
                )
                Log.i(
                    TAG,
                    "MULTITRACK MAP: container=${container.id} AUDIO ${source.streamIndex} -> A${track + 1} " +
                        "source=${source.id} start=${timelineStartMs + source.timelineOffsetMs} duration=${source.durationMs}",
                )
            }
        }
        return ImportedAdditions(clips, audio)
    }

    private fun isImageUri(uri: Uri): Boolean =
        getApplication<Application>().contentResolver.getType(uri)?.startsWith("image/") == true

    private suspend fun importImages(
        uris: List<Uri>,
        startingTrack: Int,
        timelineStartMs: Long,
    ): List<VideoClip> = withContext(Dispatchers.IO) {
        uris.mapIndexedNotNull { index, uri ->
            runCatching {
                persistReadPermission(uri)
                val resolver = getApplication<Application>().contentResolver
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream, null, options) }
                require(options.outWidth > 0 && options.outHeight > 0) { "Dimensions d’image illisibles" }
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (column >= 0) cursor.getString(column) else null
                    } else null
                } ?: uri.lastPathSegment ?: "Image"
                VideoClip(
                    sourceId = "image:${UUID.randomUUID()}",
                    mediaKind = VisualMediaKind.IMAGE,
                    timelineTrackIndex = startingTrack + index,
                    timelineStartMs = timelineStartMs,
                    uri = uri.toString(),
                    name = name,
                    durationMs = DEFAULT_IMAGE_DURATION_MS,
                    width = options.outWidth,
                    height = options.outHeight,
                    trimStartMs = 0L,
                    trimEndMs = DEFAULT_IMAGE_DURATION_MS,
                    volume = 0f,
                )
            }.onFailure { error ->
                Log.w(TAG, "Image import rejected for $uri", error)
                _userMessage.value = "Une image n’a pas pu être importée"
            }.getOrNull()
        }
    }

    private fun VideoProject.asMultitrack(): VideoProject {
        if (timelineMode == TimelineMode.MULTITRACK) return this
        var cursor = 0L
        val converted = clips.map { clip ->
            clip.copy(
                timelineTrackIndex = 0,
                timelineStartMs = cursor,
            ).also { cursor += clip.outputDurationMs }
        }
        return copy(clips = converted, timelineMode = TimelineMode.MULTITRACK)
    }

    private fun updateSelectedClip(change: (VideoClip) -> VideoClip) {
        val project = _activeProject.value ?: return
        val selected = _selectedClipId.value ?: return
        updateProject(
            project.copy(clips = project.clips.map { if (it.id == selected) change(it) else it }),
        )
    }

    private fun replaceClip(project: VideoProject, index: Int, clip: VideoClip) {
        updateProject(project.copy(clips = project.clips.toMutableList().apply { set(index, clip) }))
    }

    private fun upsertKeyframe(
        clip: VideoClip,
        sourceTimeMs: Long,
        transform: ClipTransform,
        brightness: Float,
        volume: Float,
    ): List<TransformKeyframe> {
        val existing = nearestKeyframe(clip, sourceTimeMs)
        val updated = if (existing == null) {
            clip.keyframes + TransformKeyframe(
                timeMs = sourceTimeMs.coerceIn(0, clip.sourceDurationMs),
                transform = transform,
                brightness = brightness.coerceIn(0f, 2f),
                volume = volume.coerceIn(0f, 1f),
            )
        } else {
            clip.keyframes.map {
                if (it.id == existing.id) {
                    it.copy(
                        timeMs = sourceTimeMs.coerceIn(0, clip.sourceDurationMs),
                        transform = transform,
                        brightness = brightness.coerceIn(0f, 2f),
                        volume = volume.coerceIn(0f, 1f),
                    )
                } else {
                    it
                }
            }
        }
        return updated.sortedBy(TransformKeyframe::timeMs)
    }

    private fun nearestKeyframe(clip: VideoClip, sourceTimeMs: Long): TransformKeyframe? {
        val toleranceMs = (KEYFRAME_SNAP_OUTPUT_MS * clip.speed).toLong().coerceAtLeast(60)
        return clip.keyframes.minByOrNull { kotlin.math.abs(it.timeMs - sourceTimeMs) }
            ?.takeIf { kotlin.math.abs(it.timeMs - sourceTimeMs) <= toleranceMs }
    }

    private fun upsertAudioKeyframe(
        track: AudioTrack,
        projectPositionMs: Long,
        volume: Float,
    ): List<AudioKeyframe> {
        val timeMs = projectPositionMs.coerceAtLeast(0)
        val existing = nearestAudioKeyframe(track, timeMs)
        val updated = if (existing == null) {
            track.keyframes + AudioKeyframe(timeMs = timeMs, volume = volume.coerceIn(0f, 1f))
        } else {
            track.keyframes.map {
                if (it.id == existing.id) {
                    it.copy(timeMs = timeMs, volume = volume.coerceIn(0f, 1f))
                } else {
                    it
                }
            }
        }
        return updated.sortedBy(AudioKeyframe::timeMs)
    }

    private fun nearestAudioKeyframe(track: AudioTrack, projectPositionMs: Long): AudioKeyframe? =
        track.keyframes.minByOrNull { kotlin.math.abs(it.timeMs - projectPositionMs) }
            ?.takeIf { kotlin.math.abs(it.timeMs - projectPositionMs) <= KEYFRAME_SNAP_OUTPUT_MS }

    fun undo() {
        val current = _activeProject.value ?: return
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeLast()
        redoStack.addLast(current)
        trimHistory(redoStack)
        restoreHistoryProject(previous)
    }

    fun redo() {
        val current = _activeProject.value ?: return
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        trimHistory(undoStack)
        restoreHistoryProject(next)
    }

    private fun updateProject(project: VideoProject) {
        val previous = _activeProject.value
        val candidate = project.withValidTransitions()
        if (previous != null && previous.id == candidate.id) {
            val comparable = candidate.copy(updatedAt = previous.updatedAt)
            if (comparable != previous) {
                undoStack.addLast(previous)
                trimHistory(undoStack)
                redoStack.clear()
            }
        }
        commitProject(candidate)
        updateHistoryFlags()
    }

    private fun restoreHistoryProject(snapshot: VideoProject) {
        commitProject(snapshot)
        val selected = _selectedClipId.value
        if (selected == null || snapshot.clips.none { it.id == selected }) {
            _selectedClipId.value = snapshot.clips.firstOrNull()?.id
        }
        updateHistoryFlags()
    }

    private fun commitProject(project: VideoProject) {
        val updated = project.withValidTransitions().copy(updatedAt = System.currentTimeMillis())
        _activeProject.value = updated
        _projects.value = _projects.value
            .map { if (it.id == updated.id) updated else it }
            .sortedByDescending(VideoProject::updatedAt)
        store.save(_projects.value)
    }

    private fun trimHistory(stack: ArrayDeque<VideoProject>) {
        while (stack.size > MAX_EDIT_HISTORY) stack.removeFirst()
    }

    private fun clearEditHistory() {
        undoStack.clear()
        redoStack.clear()
        updateHistoryFlags()
    }

    private fun updateHistoryFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    override fun onCleared() {
        exportManager.cancel(resetState = false)
        super.onCleared()
    }

    private data class ImportedAdditions(
        val videoClips: List<VideoClip>,
        val audioTracks: List<SourceAudioTrack>,
    )

    private companion object {
        const val TAG = "FabVidImport"
        const val MIN_CLIP_MS = 250L
        const val KEYFRAME_SNAP_OUTPUT_MS = 120L
        const val DEFAULT_IMAGE_DURATION_MS = 5_000L
        const val MAX_EDIT_HISTORY = 60
    }
}
