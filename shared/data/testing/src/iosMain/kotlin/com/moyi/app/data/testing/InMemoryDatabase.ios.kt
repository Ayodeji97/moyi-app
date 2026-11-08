package com.moyi.app.data.testing

import app.cash.sqldelight.db.SqlDriver
import com.moyi.app.data.database.MoyiDatabase

actual fun inMemoryDriver(): SqlDriver = app.cash.sqldelight.driver.native.inMemoryDriver(MoyiDatabase.Schema)
