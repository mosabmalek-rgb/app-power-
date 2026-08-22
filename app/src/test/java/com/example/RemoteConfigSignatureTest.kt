package com.example

import com.example.data.api.RemoteApiConfig
import com.example.data.api.RemoteConfigSignature
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSASecurityProvider
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.Security
import java.security.Signature
import java.util.Base64

class RemoteConfigSignatureTest {
    private val seed = "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"

    @Test
    fun `accepts valid signed configuration and rejects modified URL`() {
        if (Security.getProvider("EdDSA") == null) Security.addProvider(EdDSASecurityProvider())
        val privateSpec = EdDSAPrivateKeySpec(hexToBytes(seed), EdDSANamedCurveTable.getByName("Ed25519"))
        val privateKey = EdDSAPrivateKey(privateSpec)
        val publicKey = Base64.getEncoder().encodeToString(privateKey.getAbyte())
        val unsigned = RemoteApiConfig(1, "https://powerhub.example.com/", "")
        val signer = Signature.getInstance("NONEwithEdDSA")
        signer.initSign(privateKey)
        signer.update(requireNotNull(RemoteConfigSignature.canonicalPayload(unsigned)).toByteArray())
        val signed = unsigned.copy(signature = Base64.getEncoder().encodeToString(signer.sign()))

        assertTrue(RemoteConfigSignature.verify(publicKey, signed))
        assertFalse(RemoteConfigSignature.verify(publicKey, signed.copy(apiBaseUrl = "https://attacker.example.com/")))
    }

    private fun hexToBytes(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
