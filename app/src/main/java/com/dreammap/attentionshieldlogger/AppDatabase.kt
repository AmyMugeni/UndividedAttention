package com.dreammap.attentionshieldlogger

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [
    SessionEvent::class,
    NotificationEvent::class,
    ScrollEvent::class
], version = 11)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun notificationDao(): NotificationDao
    abstract fun scrollEventDao(): ScrollEventDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE session_events ADD COLUMN scrollCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE session_events ADD COLUMN tapCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE session_events ADD COLUMN scrollSpeedEstimate REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE session_events ADD COLUMN isPassiveSession INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE session_events ADD COLUMN isAutoplaySession INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE session_events ADD COLUMN actualVideoCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE session_events ADD COLUMN autoplayVideoCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE session_events ADD COLUMN totalPlaybackMinutes REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE session_events ADD COLUMN wasMediaDetected INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE session_events ADD COLUMN detectedFormat TEXT NOT NULL DEFAULT 'UNKNOWN'"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE session_events ADD COLUMN userId TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE session_events ADD COLUMN sessionHour INTEGER NOT NULL DEFAULT -1"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE session_events ADD COLUMN notificationCount INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE session_events ADD COLUMN notificationsInWindow INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE session_events ADD COLUMN notificationBeforeSessionMs INTEGER NOT NULL DEFAULT -1"
                )
                database.execSQL(
                    "ALTER TABLE session_events ADD COLUMN triggeredByNotification INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // NOTE: This migration had two bugs that are fixed in MIGRATION_9_10:
        //   1. Wrong index names (idx_notification_pkg_time / idx_notification_pkg_session)
        //      instead of Room's auto-generated names.
        //   2. Boolean columns created with DEFAULT 0, but Room expects no default value.
        // Do NOT change this migration — it already ran on devices at version 8→9.
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS notification_events")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS notification_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        packageName TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        sessionId INTEGER,
                        notificationTitle TEXT,
                        isOngoing INTEGER NOT NULL DEFAULT 0,
                        isForeground INTEGER NOT NULL DEFAULT 0,
                        isHeadsUp INTEGER NOT NULL DEFAULT 0
                    )
                """)
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS idx_notification_pkg_time
                    ON notification_events(packageName, timestamp)
                """)
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS idx_notification_pkg_session
                    ON notification_events(packageName, sessionId)
                """)
            }
        }

        // Fixes the two bugs introduced in MIGRATION_8_9:
        //   1. Recreates notification_events with correct schema (no DEFAULT on bool columns)
        //   2. Replaces wrong index names with Room's expected auto-generated names
        // Also adds the scroll_events table.
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {

                // Step 1: Recreate notification_events with correct schema
                database.execSQL("ALTER TABLE notification_events RENAME TO notification_events_old")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS notification_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        packageName TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        sessionId INTEGER,
                        notificationTitle TEXT,
                        isOngoing INTEGER NOT NULL,
                        isForeground INTEGER NOT NULL,
                        isHeadsUp INTEGER NOT NULL
                    )
                """)
                database.execSQL("""
                    INSERT INTO notification_events
                    SELECT id, packageName, timestamp, sessionId, notificationTitle,
                           isOngoing, isForeground, isHeadsUp
                    FROM notification_events_old
                """)
                database.execSQL("DROP TABLE notification_events_old")

                // Step 2: Create indexes with Room's expected auto-generated names
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_notification_events_packageName_timestamp`
                    ON notification_events(packageName, timestamp)
                """)
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_notification_events_packageName_sessionId`
                    ON notification_events(packageName, sessionId)
                """)

                // Step 3: Create scroll_events table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS scroll_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        packageName TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        sessionId INTEGER,
                        eventType TEXT NOT NULL,
                        scrollDelta REAL NOT NULL DEFAULT 0.0,
                        confidence REAL NOT NULL DEFAULT 0.0,
                        contentHash TEXT
                    )
                """)
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS `idx_scroll_pkg_time`
                    ON scroll_events(packageName, timestamp)
                """)
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS `idx_scroll_session`
                    ON scroll_events(sessionId)
                """)
            }
        }

        // Fixes bugs introduced in MIGRATION_9_10 for scroll_events:
        //   1. Recreates scroll_events without DEFAULT on scrollDelta and confidence
        //   2. Replaces wrong index names with Room's expected auto-generated names
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {

                // Step 1: Recreate scroll_events with correct schema (no DEFAULT on float columns)
                database.execSQL("ALTER TABLE scroll_events RENAME TO scroll_events_old")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS scroll_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        packageName TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        sessionId INTEGER,
                        eventType TEXT NOT NULL,
                        scrollDelta REAL NOT NULL,
                        confidence REAL NOT NULL,
                        contentHash TEXT
                    )
                """)
                database.execSQL("""
                    INSERT INTO scroll_events
                    SELECT id, packageName, timestamp, sessionId, eventType,
                           scrollDelta, confidence, contentHash
                    FROM scroll_events_old
                """)
                database.execSQL("DROP TABLE scroll_events_old")

                // Step 2: Create indexes with Room's expected auto-generated names
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_scroll_events_packageName_timestamp`
                    ON scroll_events(packageName, timestamp)
                """)
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_scroll_events_sessionId`
                    ON scroll_events(sessionId)
                """)
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "attentionshield.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11
                    )
                    .build().also { INSTANCE = it }
            }
        }
    }
}