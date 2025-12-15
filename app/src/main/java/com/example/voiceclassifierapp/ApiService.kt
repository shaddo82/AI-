package com.example.voiceclassifierapp

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class PredictResponse(
    val result: String,
    val scores: Map<String, Float>
)

interface ApiService {

    @Multipart
    @POST("predict")
    fun uploadAudio(
        @Part audio: MultipartBody.Part
    ): Call<PredictResponse>
}
