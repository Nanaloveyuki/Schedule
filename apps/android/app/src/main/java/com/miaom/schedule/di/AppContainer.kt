package com.miaom.schedule.di

import android.content.Context
import com.miaom.schedule.data.repository.PreferencesScheduleRepository
import com.miaom.schedule.data.repository.ScheduleRepository
import com.miaom.schedule.data.repository.ScheduleStore
import com.miaom.schedule.data.transfer.ImportDraftInbox
import com.miaom.schedule.data.transfer.ScheduleImportCoordinator
import com.miaom.schedule.platform.ocr.OcrScheduleImporter
import com.miaom.schedule.platform.network.RemoteScheduleFetcher
import com.miaom.schedule.platform.scheduler.ReminderOrchestrator
import com.miaom.schedule.platform.share.ShareImportHandler

class AppContainer(context: Context) {
    private val preferencesScheduleRepository = PreferencesScheduleRepository(context)
    private val remoteScheduleFetcher = RemoteScheduleFetcher()
    private val ocrScheduleImporter = OcrScheduleImporter(context)

    val scheduleStore: ScheduleStore = preferencesScheduleRepository
    val scheduleRepository: ScheduleRepository = preferencesScheduleRepository
    val importDraftInbox: ImportDraftInbox = ImportDraftInbox()
    val scheduleImportCoordinator: ScheduleImportCoordinator = ScheduleImportCoordinator(
        scheduleStore = scheduleStore,
        remoteScheduleFetcher = remoteScheduleFetcher
    )
    val shareImportHandler: ShareImportHandler = ShareImportHandler(
        context = context,
        importCoordinator = scheduleImportCoordinator,
        ocrScheduleImporter = ocrScheduleImporter,
        scheduleStore = scheduleStore,
        importDraftInbox = importDraftInbox
    )
    val reminderOrchestrator: ReminderOrchestrator = ReminderOrchestrator(
        context = context,
        scheduleStore = scheduleStore
    )
}
