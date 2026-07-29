package com.vidbridge.protocol

import com.vidbridge.protocol.api.LocalNetworkPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkPolicyTest {
    @Test
    fun allowsLoopbackAndPrivateAddressesOnly() {
        assertTrue(LocalNetworkPolicy.isPrivateHost("127.0.0.1"))
        assertTrue(LocalNetworkPolicy.isPrivateHost("192.168.1.20"))
        assertTrue(LocalNetworkPolicy.isPrivateHost("10.0.0.8"))
        assertFalse(LocalNetworkPolicy.isPrivateHost("8.8.8.8"))
    }
}
