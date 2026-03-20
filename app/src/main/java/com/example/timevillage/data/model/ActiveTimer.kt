package com.example.timevillage.data.model

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

@IgnoreExtraProperties
data class ActiveTimer(
    var taskId: String = "",        // local:<categoryId> или Firestore taskId
    var taskName: String = "",
    var tag: String = "tv",
    var startTime: Long = 0L,
    @get:PropertyName("running")
    @set:PropertyName("running")
    var isRunning: Boolean = false,
    var sourceApp: String = ""      // "TV" | "TDT"
)
