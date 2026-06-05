package com.example.simplesleeprecorder.domain.model

enum class SleepStageType(val label: String) {
    AWAKE("起きている"),
    DOZING("うとうと"),
    LIGHT("すやすや"),
    DEEP("ぐっすり"),
}
