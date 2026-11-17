package com.lockplayer

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class LockPlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(
            ExtractorDownloader.instance,
            Localization("it", "IT"),
            ContentCountry("IT"),
        )
    }
}
