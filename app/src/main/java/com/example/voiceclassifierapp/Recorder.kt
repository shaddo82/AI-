package com.example.voiceclassifierapp

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs

class Recorder(
    private val context: Context,
    private val onSegmentReady: (File) -> Unit
) {

    private val sampleRate = 16000

    // 20ms 프레임 (16000 * 0.02 * 2 bytes)
    private val frameSize = (sampleRate * 0.02f * 2).toInt()

    // 무음이 이 프레임 수 이상 지속되면 세그먼트 종료
    private val silenceFrameLimit = 15   // 약 300ms

    // 너무 짧은 세그먼트 제거 (잡음 방지)
    private val minSegmentBytes = sampleRate * 2 * 1   // 1초

    private var recorder: AudioRecord? = null
    private var isRecording = false

    fun start() {
        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            frameSize * 2
        )

        recorder?.startRecording()
        isRecording = true

        Thread {
            recordLoop()
        }.start()
    }

    fun stop() {
        isRecording = false
        recorder?.stop()
        recorder?.release()
        recorder = null
    }

    private fun recordLoop() {
        val frameBuffer = ByteArray(frameSize)
        val speechBuffer = ByteArrayOutputStream()

        var silenceFrames = 0

        while (isRecording) {
            val read = recorder?.read(frameBuffer, 0, frameBuffer.size) ?: 0
            if (read <= 0) continue

            if (!isSilent(frameBuffer)) {
                // 음성 프레임
                speechBuffer.write(frameBuffer)
                silenceFrames = 0
            } else {
                // 무음 프레임
                silenceFrames++
            }

            // 무음이 충분히 이어졌고, 음성이 쌓여 있으면 세그먼트 종료
            if (silenceFrames >= silenceFrameLimit &&
                speechBuffer.size() >= minSegmentBytes
            ) {
                flushSegment(speechBuffer)
                silenceFrames = 0
            }
        }

        // 녹음 종료 시 남은 음성 처리
        if (speechBuffer.size() >= minSegmentBytes) {
            flushSegment(speechBuffer)
        }
    }

    private fun flushSegment(speechBuffer: ByteArrayOutputStream) {
        val pcmBytes = speechBuffer.toByteArray()
        speechBuffer.reset()

        val pcmFile = File(
            context.filesDir,
            "segment_${System.currentTimeMillis()}.pcm"
        )
        pcmFile.writeBytes(pcmBytes)

        val wavFile = File(
            pcmFile.absolutePath.replace(".pcm", ".wav")
        )

        PcmToWavConverter.convert16bitMono(
            pcmFile.absolutePath,
            wavFile.absolutePath,
            sampleRate
        )

        onSegmentReady(wavFile)

        pcmFile.delete()
    }

    // =========================
    // VAD: 에너지 + 비율 기반
    // =========================
    private fun isSilent(
        pcm: ByteArray,
        energyThreshold: Int = 200,
        activeRatioThreshold: Float = 0.02f
    ): Boolean {

        var activeCount = 0
        var totalCount = 0

        var i = 0
        while (i < pcm.size - 1) {
            val sample =
                (pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xff)

            if (abs(sample) > energyThreshold) {
                activeCount++
            }
            totalCount++
            i += 2
        }

        val ratio =
            if (totalCount > 0) activeCount.toFloat() / totalCount else 0f

        // 일정 비율 이상 에너지가 있으면 음성
        return ratio < activeRatioThreshold
    }
}
]]