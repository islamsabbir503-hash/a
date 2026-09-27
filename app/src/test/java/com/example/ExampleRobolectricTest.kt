package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("NitroWarp VPN", appName)
  }

  @Test
  fun `test crypto utils X25519 keypair and flag generation`() {
    val keyPair = com.example.data.crypto.CryptoUtils.generateX25519KeyPair()
    val privateKey = keyPair.first
    val publicKey = keyPair.second
    org.junit.Assert.assertNotNull(privateKey)
    org.junit.Assert.assertNotNull(publicKey)
    org.junit.Assert.assertTrue(publicKey.isNotEmpty())

    val flag = com.example.data.crypto.CryptoUtils.countryCodeToFlagEmoji("JP")
    assertEquals("🇯🇵", flag)

    val usFlag = com.example.data.crypto.CryptoUtils.countryCodeToFlagEmoji("US")
    assertEquals("🇺🇸", usFlag)
  }
}
