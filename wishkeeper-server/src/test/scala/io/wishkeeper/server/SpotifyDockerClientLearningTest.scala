package io.wishkeeper.server

import com.datastax.driver.core.Cluster
import com.spotify.docker.client.messages.{ContainerConfig, HostConfig, PortBinding}
import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.JavaConverters._

class SpotifyDockerClientLearningTest extends FlatSpec with Eventually with Matchers with IntegrationPatience {

  it should "start an instance of a docker image" in {
    val docker = DefaultDockerClient.fromEnv().build()

    val cassandraImage = "cassandra:3.9"
    docker.pull(cassandraImage)

    val port = "9042"
    val portBindings = Map(port → List(PortBinding.of("0.0.0.0", port)).asJava)

    val hostConfig = HostConfig.builder().portBindings(portBindings.asJava).build()

    val containerConfig = ContainerConfig.builder().
      hostConfig(hostConfig).
      image(cassandraImage).
      exposedPorts(port).
      build()

    val containerCreation = docker.createContainer(containerConfig)
    val id = containerCreation.id

    docker.startContainer(id)


    eventually{
      val logs = docker.logs(id, DockerClient.LogsParam.stdout(), DockerClient.LogsParam.stderr())
      logs.readFully() should include("Starting listening for CQL clients on")
    }

    val cluster = Cluster.builder().addContactPoint("localhost").build()
    val session = cluster.connect()
    println(session.getCluster.getMetadata.getClusterName)
    session.close()
    cluster.close()

    docker.killContainer(id)
    docker.removeContainer(id)
    docker.close()
  }

}
