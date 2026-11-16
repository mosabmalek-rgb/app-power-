package com.example

import android.content.Context
import androidx.room.Room
import com.example.data.FieldReadingDatabase
import com.example.data.SubscriberEntity
import com.example.data.PendingReadingEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("PowerHub", appName)
  }

  @Test
  fun `launch MainActivity without crash`() {
    org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
  }
  private fun subscriber(id: String, branch: String = "1") = SubscriberEntity(
    id = id, refNumber = id, name = "Subscriber $id", phone = "",
    address = "", branchId = branch, lastReading = 0.0,
    lastReadingDate = "", balance = 0.0
  )

  @Test
  fun `failed duplicate snapshot preserves previous cache`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, FieldReadingDatabase::class.java)
      .allowMainThreadQueries().build()
    try {
      val dao = db.dao()
      dao.replaceSubscribersSnapshot(listOf(subscriber("101")))
      val failed = runCatching {
        dao.replaceSubscribersSnapshot(listOf(subscriber("102"), subscriber("102")))
      }
      assertTrue(failed.isFailure)
      assertEquals(listOf("101"), dao.getAllSubscriberIds())
    } finally {
      db.close()
    }
  }

  @Test
  fun `empty snapshot cannot erase offline subscriber cache`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, FieldReadingDatabase::class.java)
      .allowMainThreadQueries().build()
    try {
      val dao = db.dao()
      dao.replaceSubscribersSnapshot(listOf(subscriber("101")))
      assertTrue(runCatching { dao.replaceSubscribersSnapshot(emptyList()) }.isFailure)
      assertEquals(listOf("101"), dao.getAllSubscriberIds())
    } finally {
      db.close()
    }
  }

  @Test
  fun `snapshot retains subscriber referenced by unsent reading`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, FieldReadingDatabase::class.java)
      .allowMainThreadQueries().build()
    try {
      val dao = db.dao()
      dao.replaceSubscribersSnapshot(listOf(subscriber("101"), subscriber("102")))
      dao.insertPendingReading(
        PendingReadingEntity(
          subscriberId = "101", currentReading = 120.0,
          readingDate = "2026-11-16 10:00", notes = "",
          latitude = 0.0, longitude = 0.0, photoPath = null,
          status = "pending"
        )
      )
      dao.replaceSubscribersSnapshot(listOf(subscriber("102"), subscriber("103")))
      assertEquals(setOf("101", "102", "103"), dao.getAllSubscriberIds().toSet())
      assertEquals(1, dao.getReadingsForSubscriber("101").size)
      dao.replaceSubscribersSnapshot(listOf(subscriber("103")))
      assertEquals(setOf("101", "103"), dao.getAllSubscriberIds().toSet())
    } finally {
      db.close()
    }
  }

}
