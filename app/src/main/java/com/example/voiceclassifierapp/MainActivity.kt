package com.example.voiceclassifierapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.LinearLayout
import android.view.View
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var recorder: Recorder

    private lateinit var txtStatus: TextView
    private lateinit var txtLiveResult: TextView
    private lateinit var txtScore: TextView
    private lateinit var txtFinalResult: TextView

    // 🔥 새로 추가
    private lateinit var txtCurrentState: TextView
    private lateinit var txtStateDesc: TextView
    private lateinit var timelineContainer: LinearLayout

    // 사람 / AI 음성 히스토리
    private val userHistory = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissionsIfNeeded()

        val btnRecord = findViewById<Button>(R.id.btnRecord)
        val btnStop = findViewById<Button>(R.id.btnStop)

        txtStatus = findViewById(R.id.txtStatus)
        txtLiveResult = findViewById(R.id.txtLiveResult)
        txtScore = findViewById(R.id.txtScore)
        txtFinalResult = findViewById(R.id.txtFinalResult)

        txtCurrentState = findViewById(R.id.txtCurrentState)
        txtStateDesc = findViewById(R.id.txtStateDesc)
        timelineContainer = findViewById(R.id.timelineContainer)

        // =====================
        // 녹음 시작
        // =====================
        btnRecord.setOnClickListener {

            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                txtStatus.text = "녹음 권한 없음"
                requestPermissionsIfNeeded()
                return@setOnClickListener
            }

            userHistory.clear()
            timelineContainer.removeAllViews()

            txtStatus.text = "녹음 및 분석 중..."
            txtCurrentState.text = "대기"
            txtCurrentState.setTextColor(0xFF555555.toInt())
            txtStateDesc.text = "음성을 기다리는 중"
            txtLiveResult.text = ""
            txtScore.text = ""
            txtFinalResult.text = "녹음 종료 후 최종 결과 표시"

            recorder = Recorder(this) { wavFile ->
                uploadChunk(wavFile)
            }

            recorder.start()
        }

        // =====================
        // 녹음 종료
        // =====================
        btnStop.setOnClickListener {

            recorder.stop()
            txtStatus.text = "녹음 종료"

            if (userHistory.isEmpty()) {
                txtFinalResult.text = "음성이 감지되지 않았습니다"
                return@setOnClickListener
            }

            val aiCount = userHistory.count { it == "AI 음성" }
            val ratio = aiCount.toFloat() / userHistory.size

            txtFinalResult.text =
                if (ratio >= 0.5f) "최종 판별 결과: AI 음성"
                else "최종 판별 결과: 사람"
        }
    }

    // =====================
    // 3초마다 자동 업로드
    // =====================
    private fun uploadChunk(wavFile: File) {
        val requestBody =
            wavFile.asRequestBody("audio/wav".toMediaType())
        val part =
            MultipartBody.Part.createFormData("audio", wavFile.name, requestBody)

        RetrofitClient.instance.uploadAudio(part)
            .enqueue(object : Callback<PredictResponse> {

                override fun onResponse(
                    call: Call<PredictResponse>,
                    response: Response<PredictResponse>
                ) {
                    val body = response.body() ?: return

                    val userClass = mapToUserClass(body.result)
                    userHistory.add(userClass)

                    runOnUiThread {

                        if (userClass == "AI 음성") {
                            txtCurrentState.text = "AI 음성"
                            txtCurrentState.setTextColor(0xFFFF4444.toInt())
                            txtStateDesc.text = "AI 음성이 감지되었습니다"
                            addTimelineBlock(isAI = true)
                        } else {
                            txtCurrentState.text = "사람"
                            txtCurrentState.setTextColor(0xFF4CAF50.toInt())
                            txtStateDesc.text = "사람이 말하고 있습니다"
                            addTimelineBlock(isAI = false)
                        }

                        txtStatus.text = "분석 중..."
                        txtLiveResult.text = "현재 판별: $userClass"

                        txtScore.text = body.scores.entries.joinToString("\n") {
                            "${it.key}: ${(it.value * 100).toInt()}%"
                        }
                    }
                }

                override fun onFailure(call: Call<PredictResponse>, t: Throwable) {
                    runOnUiThread {
                        txtStatus.text = "서버 오류"
                        txtStateDesc.text = "분석 실패"
                    }
                }
            })
    }

    // =====================
    // 모델 → 사용자 클래스 매핑
    // =====================
    private fun mapToUserClass(modelResult: String): String {
        return if (modelResult == "orig") "사람" else "AI 음성"
    }

    // =====================
    // 타임라인 블록 추가
    // =====================
    private fun addTimelineBlock(isAI: Boolean) {
        val block = View(this)
        val width = (resources.displayMetrics.density * 20).toInt()

        val params = LinearLayout.LayoutParams(
            width,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        params.marginEnd = (resources.displayMetrics.density * 4).toInt()

        block.layoutParams = params
        block.setBackgroundColor(
            if (isAI) 0xFFFF4444.toInt() else 0xFF4CAF50.toInt()
        )

        timelineContainer.addView(block)
    }

    // =====================
    // 권한 처리
    // =====================
    private fun requestPermissionsIfNeeded() {
        val perms = arrayOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        var need = false
        for (p in perms) {
            if (ContextCompat.checkSelfPermission(this, p)
                != PackageManager.PERMISSION_GRANTED
            ) {
                need = true
                break
            }
        }

        if (need) {
            ActivityCompat.requestPermissions(this, perms, 100)
        }
    }
}
