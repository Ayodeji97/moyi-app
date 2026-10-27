package com.moyi.app.data.session

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformSessionModule: Module = module {
    single<SecureTokenStore> { AndroidKeystoreTokenStore(androidContext()) }
}
