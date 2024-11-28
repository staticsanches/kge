package dev.staticsanches.kge.spi

import dev.staticsanches.kge.test.KGEMockableRunner
import dev.staticsanches.kge.test.MockSPIFile
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(KGEMockableRunner::class)
@MockSPIFile(target = KGESPIExtensibleTest.Service::class, implementation = KGESPIExtensibleTest.ServiceImpl1::class)
@MockSPIFile(target = KGESPIExtensibleTest.Service::class, implementation = KGESPIExtensibleTest.ServiceImpl2::class)
@MockSPIFile(target = KGESPIExtensibleTest.Service::class, implementation = KGESPIExtensibleTest.ServiceImpl3::class)
class KGESPIExtensibleTest {
    @Test
    fun checkServicePriority() {
        val service = KGESPIExtensible.getOptionalWithHigherPriority<Service>()
        Assert.assertTrue(service is ServiceImpl2)
    }

    interface Service : KGESPIExtensible

    class ServiceImpl1 : Service {
        override val servicePriority: Int
            get() = 10
    }

    class ServiceImpl2 : Service {
        override val servicePriority: Int
            get() = 15
    }

    class ServiceImpl3 : Service {
        override val servicePriority: Int
            get() = 1
    }
}
