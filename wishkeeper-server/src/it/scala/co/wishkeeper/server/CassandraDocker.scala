package co.wishkeeper.server

import java.util.concurrent.atomic.AtomicBoolean

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient.ListContainersParam.allContainers
import com.spotify.docker.client.DockerClient.LogsParam
import com.spotify.docker.client.messages.{ContainerConfig, HostConfig, PortBinding}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.duration._


class CassandraDocker {
  val containerName = "cassandra-test-kit"
  val docker = DefaultDockerClient.fromEnv().build()
  val cassandraImage = "cassandra:3.9"
  val port = "9042"

  private def start() = {
    docker.pull(cassandraImage)

    val portBindings = Map(port → List(PortBinding.of("0.0.0.0", port)).asJava)

    val hostConfig = HostConfig.builder().portBindings(portBindings.asJava).build()
    val containerConfig = ContainerConfig.builder().
      hostConfig(hostConfig).
      image(cassandraImage).
      exposedPorts(port).
      build()

    removeContainerIfAlreadyExists()

    val containerCreation = docker.createContainer(containerConfig, containerName)
    val id = containerCreation.id

    docker.startContainer(id)

    waitForUpMessage(id)

    this
  }


  private def waitForUpMessage(containerId: String, retries: Int = 240, backoff: Duration = 250.millis) = {
    (1 to retries).find(_ ⇒ {
      Thread.sleep(backoff.toMillis)
      val logs = docker.logs(containerId, LogsParam.stdout(), LogsParam.stderr())
      logs.readFully().contains("Starting listening for CQL clients on")
    }) match {
      case Some(_) ⇒ CassandraDocker.log.info(s"Cassandra Docker started. Listening on port $port")
      case None ⇒ throw new RuntimeException(s"Failed starting Cassandra Docker. See container $containerName logs for more info.")
    }
  }

  private def removeContainerIfAlreadyExists() = {
    docker.listContainers(allContainers()).asScala.find(_.names().contains("/" + containerName)).foreach { container ⇒
      if (container.state() == "running")
        docker.killContainer(container.id())
      docker.removeContainer(container.id())
    }
  }
}

object CassandraDocker {
  private val log = LoggerFactory.getLogger(classOf[CassandraDocker])
  private var instance: CassandraDocker = _
  private val initialized = new AtomicBoolean(false)

  def start(): CassandraDocker = {
    if (initialized.compareAndSet(false, true)) {
      log.info("Starting Cassandra Docker container.")
      instance = new CassandraDocker().start()
    }
    instance
  }
}