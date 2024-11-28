package dev.staticsanches.kge.test

import org.junit.runner.Description
import org.junit.runner.Runner
import org.junit.runner.notification.RunNotifier
import org.junit.runners.JUnit4
import java.net.URL
import java.net.URLClassLoader
import java.util.Collections
import java.util.Enumeration

/**
 * [Runner] that uses a separate [ClassLoader] for each test class and allows the use of @[MockSPIFile].
 *
 * It's helpful when it's needed to change global variables and these changes must not impact other tests.
 */
class KGEMockableRunner(
    testClass: Class<*>,
) : Runner() {
    private val loader = KGEClassLoader()
    private val resources: MutableMap<String, MutableList<URL>> = LinkedHashMap()

    private val innerRunner: Runner =
        loader
            .loadClass<JUnit4>()
            .getConstructor(loader.loadClass<Class<*>>())
            .newInstance(loader.loadClass(testClass.name)) as Runner

    init {
        testClass.mockedSPIFiles.forEach { r -> resources.computeIfAbsent(r.name) { mutableListOf<URL>() }.add(r.url) }
    }

    override fun getDescription(): Description? = innerRunner.description

    override fun run(notifier: RunNotifier?) {
        val thread = Thread.currentThread()
        val originalLoader = thread.contextClassLoader
        thread.contextClassLoader = loader
        try {
            innerRunner.run(notifier)
        } finally {
            thread.contextClassLoader = originalLoader
            loader.close()
        }
    }

    data class Resource(
        val name: String,
        val url: URL,
    )

    private inner class KGEClassLoader(
        parent: ClassLoader = getSystemClassLoader(),
    ) : URLClassLoader(parent.urls, parent) {
        inline fun <reified T> loadClass(): Class<*> = loadClass(T::class.java.name)!!

        override fun loadClass(name: String?): Class<*>? =
            if (name?.startsWith("dev.staticsanches.kge.") == true) {
                findLoadedClass(name) ?: findClass(name)
            } else {
                super.loadClass(name)
            }

        override fun findResources(name: String?): Enumeration<URL?> {
            val customResources = resources[name]
            val superResources = super.findResources(name)
            if (customResources?.isNotEmpty() == true) {
                if (superResources.hasMoreElements()) {
                    val combined = customResources.toMutableList()
                    combined += superResources.toList()
                    return Collections.enumeration<URL>(combined)
                } else {
                    return Collections.enumeration<URL>(customResources)
                }
            } else {
                return superResources
            }
        }
    }

    companion object {
        private val ClassLoader.urls: Array<URL>
            get() {
                val field = this.javaClass.getDeclaredField("ucp")
                field.isAccessible = true
                val ucp = field.get(this)
                val method = ucp.javaClass.getDeclaredMethod("getURLs")
                method.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                return method.invoke(ucp) as Array<URL>
            }
    }
}
