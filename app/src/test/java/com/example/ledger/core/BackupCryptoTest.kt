package com.example.ledger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    @Test fun roundTrip() {
        val text = "{\"transactions\":[{\"amountPaise\":36500,\"merchant\":\"PRADEEP SWEETS\"}]}".repeat(200)
        val file = BackupCrypto.encrypt(text.toByteArray(), "my secret 123".toCharArray())
        assertTrue(file.size < text.length) // compressed
        assertEquals(text, String(BackupCrypto.decrypt(file, "my secret 123".toCharArray())))
    }

    @Test fun wrongPasswordIsRejected() {
        val file = BackupCrypto.encrypt("hello".toByteArray(), "right".toCharArray())
        var rejected = false
        try { BackupCrypto.decrypt(file, "wrong".toCharArray()) } catch (e: BackupCrypto.WrongPasswordOrFile) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun tamperedOrForeignFileIsRejected() {
        val file = BackupCrypto.encrypt("hello".toByteArray(), "pw".toCharArray())
        file[file.size - 1] = (file[file.size - 1] + 1).toByte()
        var rejected = false
        try { BackupCrypto.decrypt(file, "pw".toCharArray()) } catch (e: BackupCrypto.WrongPasswordOrFile) { rejected = true }
        assertTrue(rejected)
        var foreign = false
        try { BackupCrypto.decrypt("PK\u0003\u0004 not ours".toByteArray(), "pw".toCharArray()) } catch (e: BackupCrypto.WrongPasswordOrFile) { foreign = true }
        assertTrue(foreign)
    }
}
