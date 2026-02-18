package config.services

import org.testcontainers.containers.ComposeContainer
import org.testcontainers.lifecycle.Startable
import java.io.*

abstract class ServiceBase : Startable {
    companion object {
        private val context = System.getProperties().getOrDefault("context", "")
        private val logDir = File("target/logs/$context")
        init {
            logDir.deleteRecursively()
            logDir.mkdirs()
        }
        // Force docker client API to 1.44 for containerised docker-compose (GH runners docker 27)
        init {
            System.setProperty("DOCKER_API_VERSION", System.getProperty("DOCKER_API_VERSION", "1.44"))
        }
    }

    abstract val container: ComposeContainer

    open val logServices: List<String> = emptyList()
    private val logWriters: MutableList<Writer> = mutableListOf()

    override fun start() {
        logServices.forEach {
            val output = File(logDir, "$it.log")
            output.createNewFile()
            val writer = FileOutputStream(output, true).bufferedWriter()
            logWriters.add(writer)
            container.withLogConsumer(it) { logLine ->
                writer.append(logLine.utf8String)
            }
        }
        container.start()
        postStart()
    }

    open fun postStart() {
    }

    override fun stop() {
        Thread.sleep(5000) //wait 5 seconds to write the meaningful log
        logWriters.forEach {
            it.close()
        }
        container.stop()
    }
}
