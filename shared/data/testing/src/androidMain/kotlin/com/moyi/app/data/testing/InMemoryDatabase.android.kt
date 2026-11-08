package com.moyi.app.data.testing

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.moyi.app.data.database.MoyiDatabase

// The Android host tests run on the JVM, where the Android driver (it needs a
// Context and the framework's SQLite) cannot; the JDBC SQLite driver stands in.
actual fun inMemoryDriver(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { MoyiDatabase.Schema.create(it) }
