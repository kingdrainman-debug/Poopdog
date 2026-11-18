package com.poopdog.vr

import android.app.Application
import android.content.Context
import com.poopdog.vr.convert.VrConverter
import com.poopdog.vr.data.MediaRepository
import com.poopdog.vr.data.PlaybackMemory
import com.poopdog.vr.data.QueueRepository
import com.poopdog.vr.data.SettingsRepository

/** Holds the app-wide repositories (simple manual dependency injection). */
class VrApp : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var queue: QueueRepository
        private set
    lateinit var memory: PlaybackMemory
        private set
    lateinit var media: MediaRepository
        private set
    lateinit var converter: VrConverter
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        queue = QueueRepository(this)
        memory = PlaybackMemory(this)
        media = MediaRepository(this)
        converter = VrConverter(this)
    }

    companion object {
        fun from(context: Context): VrApp = context.applicationContext as VrApp
    }
}
