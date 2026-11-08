package com.moyi.app.data.testing

import app.cash.sqldelight.db.SqlDriver
import com.moyi.app.data.database.LocalDatabase

/** A fresh, empty, in-memory driver with the app's schema; nothing touches the disk. */
expect fun inMemoryDriver(): SqlDriver

/** A fresh, empty [LocalDatabase] over [inMemoryDriver]. */
fun inMemoryDatabase(): LocalDatabase = LocalDatabase(inMemoryDriver())
