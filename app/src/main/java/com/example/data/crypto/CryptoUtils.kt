package com.example.data.crypto

import android.util.Base64
import com.wireguard.crypto.KeyPair
import java.math.BigInteger
import java.security.SecureRandom
import java.util.regex.Pattern

object CryptoUtils {

    fun countryCodeToFlagEmoji(countryCode: String?): String {
        if (countryCode.isNullOrBlank() || countryCode.length != 2) return "🌐"
        val upper = countryCode.trim().uppercase()
        if (upper == "XX") return "🌐"
        val c0 = upper[0]
        val c1 = upper[1]
        if (c0 !in 'A'..'Z' || c1 !in 'A'..'Z') return "🌐"

        val firstChar = Character.codePointAt(upper, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(upper, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }

    fun parseOvpnConfig(base64Data: String?): Triple<String, Int, String> {
        if (base64Data.isNullOrBlank()) {
            return Triple("TCP", 443, "AES-128-CBC")
        }

        return try {
            val sanitizedB64 = base64Data.replace("\\s+".toRegex(), "")
            val decodedBytes = Base64.decode(sanitizedB64, Base64.DEFAULT)
            val configText = String(decodedBytes, Charsets.UTF_8)

            var proto = "TCP"
            var port = 443
            var cipher = "AES-128-CBC"

            // Matches "proto tcp", "proto tcp-client", "proto udp", "proto tcp4", etc.
            val protoMatcher = Pattern.compile(
                "(?m)^proto\\s+(udp|tcp)",
                Pattern.CASE_INSENSITIVE
            ).matcher(configText)
            if (protoMatcher.find()) {
                proto = protoMatcher.group(1)?.uppercase() ?: "TCP"
            }

            // Matches "remote <ip_or_host> <port>"
            val remoteMatcher = Pattern.compile(
                "(?m)^remote\\s+[^\\s]+\\s+(\\d+)",
                Pattern.CASE_INSENSITIVE
            ).matcher(configText)
            if (remoteMatcher.find()) {
                port = remoteMatcher.group(1)?.toIntOrNull() ?: 443
            } else {
                // Fallback: standalone "port <number>" line
                val portMatcher = Pattern.compile(
                    "(?m)^port\\s+(\\d+)",
                    Pattern.CASE_INSENSITIVE
                ).matcher(configText)
                if (portMatcher.find()) {
                    port = portMatcher.group(1)?.toIntOrNull() ?: 443
                }
            }

            // Matches "cipher AES-128-CBC" or "data-ciphers AES-256-GCM:AES-128-CBC"
            val cipherMatcher = Pattern.compile(
                "(?m)^cipher\\s+([^\\s\\r\\n]+)",
                Pattern.CASE_INSENSITIVE
            ).matcher(configText)
            if (cipherMatcher.find()) {
                cipher = cipherMatcher.group(1)?.trim() ?: "AES-128-CBC"
            } else {
                val dataCipherMatcher = Pattern.compile(
                    "(?m)^data-ciphers\\s+([^:\\s\\r\\n]+)",
                    Pattern.CASE_INSENSITIVE
                ).matcher(configText)
                if (dataCipherMatcher.find()) {
                    cipher = dataCipherMatcher.group(1)?.trim() ?: "AES-128-CBC"
                }
            }

            Triple(proto, port, cipher)
        } catch (_: Exception) {
            Triple("TCP", 443, "AES-128-CBC")
        }
    }

    private val P = BigInteger.valueOf(2).pow(255).subtract(BigInteger.valueOf(19))
    private val A24 = BigInteger.valueOf(121665)

    fun generateX25519KeyPair(): Pair<String, String> {
        return try {
            val wgKeyPair = KeyPair()
            Pair(wgKeyPair.privateKey.toBase64(), wgKeyPair.publicKey.toBase64())
        } catch (_: Throwable) {
            val random = SecureRandom()
            val privateKey = ByteArray(32)
            random.nextBytes(privateKey)

            // RFC 7748 Clamping
            privateKey[0] = (privateKey[0].toInt() and 248).toByte()
            privateKey[31] = (privateKey[31].toInt() and 127).toByte()
            privateKey[31] = (privateKey[31].toInt() or 64).toByte()

            val publicKey = curve25519(privateKey, BigInteger.valueOf(9))

            val privBase64 = Base64.encodeToString(privateKey, Base64.NO_WRAP)
            val pubBase64 = Base64.encodeToString(publicKey, Base64.NO_WRAP)
            Pair(privBase64, pubBase64)
        }
    }

    private fun curve25519(scalarBytes: ByteArray, uCoordinate: BigInteger): ByteArray {
        var x1 = uCoordinate
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = uCoordinate
        var z3 = BigInteger.ONE
        var swap = 0

        val k = BigInteger(1, scalarBytes.reversedArray())

        for (t in 254 downTo 0) {
            val kt = if (k.testBit(t)) 1 else 0
            swap = swap xor kt
            if (swap == 1) {
                var temp = x2; x2 = x3; x3 = temp
                temp = z2; z2 = z3; z3 = temp
            }
            swap = kt

            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)

            x3 = da.add(cb).mod(P).pow(2).mod(P)
            z3 = x1.multiply(da.subtract(cb).mod(P).pow(2).mod(P)).mod(P)
            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply(aa.add(A24.multiply(e).mod(P))).mod(P)
        }

        if (swap == 1) {
            var temp = x2; x2 = x3; x3 = temp
            var tempZ = z2; z2 = z3; z3 = tempZ
        }

        val resultBigInt = x2.multiply(z2.modInverse(P)).mod(P)
        val resultLittleEndian = ByteArray(32)
        val rawBytes = resultBigInt.toByteArray()
        for (i in 0 until minOf(32, rawBytes.size)) {
            resultLittleEndian[i] = rawBytes[rawBytes.size - 1 - i]
        }
        return resultLittleEndian
    }
}
