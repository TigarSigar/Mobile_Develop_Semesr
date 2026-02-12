package com.example.timevillage.data.remote

import retrofit2.http.GET

interface ApiService {
    @GET("daily_goal")
    suspend fun getDailyGoal(): DailyGoalResponse
}

data class DailyGoalResponse(val goal: String, val reward: Int)