package dev.staticsanches.kge.test

import kotlin.reflect.KClass

@Repeatable
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class MockSPIFile(
    val target: KClass<*>,
    val implementation: KClass<*>,
)

val Class<*>.mockedSPIFiles: List<KGEMockableRunner.Resource>
    get() {
        val mockedTypes = LinkedHashMap<KClass<*>, MutableSet<KClass<*>>>()
        getAnnotationsByType(MockSPIFile::class.java).forEach { ann ->
            mockedTypes
                .computeIfAbsent(ann.target) { LinkedHashSet<KClass<*>>() }
                .add(ann.implementation)
        }
        return mockedTypes.map { (service, implementations) ->
            val name = "META-INF/services/" + service.java.name
            val content = implementations.map { it.java.name }.joinToString("\n")
            return@map KGEMockableRunner.Resource(
                name,
                createInMemoryURL(name) { content.byteInputStream(Charsets.UTF_8) },
            )
        }
    }
