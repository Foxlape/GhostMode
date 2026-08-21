package com.ghostmode.app.data

enum class SimSlotMode(val slots: List<Int>) {
    ALL(listOf(0, 1)),
    SIM_1(listOf(0)),
    SIM_2(listOf(1));

    companion object {
        fun fromStorage(value: String?): SimSlotMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ALL
    }
}
