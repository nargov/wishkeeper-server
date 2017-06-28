package co.wishkeeper.server

import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference

import com.spotify.docker.client.{DefaultDockerClient, ProgressHandler}
import com.spotify.docker.client.DockerClient.ListContainersParam.allContainers
import com.spotify.docker.client.DockerClient.{ListImagesParam, LogsParam}
import com.spotify.docker.client.messages.{ContainerConfig, HostConfig, PortBinding, ProgressMessage}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.duration._


class CassandraDocker {
  val imageName = "cassandra-test-kit"
  val containerName = "cassandra-test-kit"
  val docker = DefaultDockerClient.fromEnv().build()
  val port = "9042"

  private def start() = {
    createImageIfNotExists()

    val portBindings = Map(port → List(PortBinding.of("0.0.0.0", port)).asJava)

    val hostConfig = HostConfig.builder().portBindings(portBindings.asJava).build()
    val containerConfig = ContainerConfig.builder().
      hostConfig(hostConfig).
      image(imageName).
      exposedPorts(port).
      build()

    removeContainerIfAlreadyExists()

    val id = docker.createContainer(containerConfig, containerName).id

    docker.startContainer(id)

    waitForUpMessage(id)

    this
  }


  private def createImageIfNotExists() = {
    if (docker.listImages(ListImagesParam.byName(imageName)).isEmpty) {
      println(s"Creating $imageName Docker image")
      val dockerfilePath = Paths.get(getClass.getResource("/CassandraDockerTestKit/testkit-cassandra.yaml").getPath).getParent
      docker.build(dockerfilePath, imageName, new ProgressHandler {
        override def progress(message: ProgressMessage): Unit = {
          println(message.stream())
        }
      })
    }
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
  private val instance = new AtomicReference[Option[CassandraDocker]](None)

  def start(): Unit = {
    if (instance.compareAndSet(None, Some(new CassandraDocker().start()))) {
      log.info("Starting Cassandra Docker container.")
    }
    else {
      log.info("Cassandra Docker already started")
    }
  }
}