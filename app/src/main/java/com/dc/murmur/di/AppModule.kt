package com.dc.murmur.di

import com.dc.murmur.ai.AnalysisPipeline
import com.dc.murmur.ai.AnalysisStateHolder
import com.dc.murmur.ai.BridgeStatusHolder
import com.dc.murmur.ai.AudioDecoder
import com.dc.murmur.ai.ModelManager
import com.dc.murmur.ai.nlp.ClaudeCodeAnalyzer
import com.dc.murmur.ai.nlp.KeywordExtractor
import com.dc.murmur.ai.nlp.SentimentAnalyzer
import com.dc.murmur.core.util.BatteryUtil
import com.dc.murmur.core.util.NotificationUtil
import com.dc.murmur.core.util.StorageUtil
import com.dc.murmur.core.util.TermuxBridgeManager
import com.dc.murmur.data.local.MurmurDatabase
import com.dc.murmur.data.repository.AnalysisRepository
import com.dc.murmur.data.repository.BatteryRepository
import com.dc.murmur.data.repository.RecordingRepository
import com.dc.murmur.data.repository.SettingsRepository
import com.dc.murmur.feature.home.HomeViewModel
import com.dc.murmur.feature.recordings.RecordingsViewModel
import com.dc.murmur.feature.stats.StatsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val databaseModule = module {
    single { MurmurDatabase.create(androidContext()) }
    single { get<MurmurDatabase>().recordingChunkDao() }
    single { get<MurmurDatabase>().sessionDao() }
    single { get<MurmurDatabase>().batteryLogDao() }
    single { get<MurmurDatabase>().transcriptionDao() }
}

val utilModule = module {
    single { StorageUtil(androidContext()) }
    single { BatteryUtil(androidContext()) }
    single { NotificationUtil(androidContext()) }
    single { TermuxBridgeManager(androidContext()) }
}

val aiModule = module {
    single { AudioDecoder() }
    single { ModelManager(androidContext()) }
    single { SentimentAnalyzer(androidContext()) }
    single { KeywordExtractor() }
    single { ClaudeCodeAnalyzer(get()) }
    single { AnalysisPipeline(get(), get(), get(), get(), get(), get()) }
    single { AnalysisStateHolder() }
    single { BridgeStatusHolder() }
}

val repositoryModule = module {
    single { RecordingRepository(get(), get(), get()) }
    single { BatteryRepository(get(), get()) }
    single { SettingsRepository(androidContext()) }
    single { AnalysisRepository(get(), get()) }
}

val viewModelModule = module {
    viewModel { HomeViewModel(get(), get(), get(), get(), get()) }
    viewModel { RecordingsViewModel(get()) }
    viewModel { StatsViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}

val appModules = listOf(
    databaseModule,
    utilModule,
    aiModule,
    repositoryModule,
    viewModelModule
)
