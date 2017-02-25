package io.wishkeeper.server

import com.whisk.docker.{DockerContainer, DockerKit, DockerReadyChecker}

trait CassandraDocker extends DockerKit {
  val DefaultCqlPort = 9042

  val cassandraContainer: DockerContainer = DockerContainer("cassandra:3.9")
    .withPorts(DefaultCqlPort -> Some(DefaultCqlPort))
    .withReadyChecker(DockerReadyChecker.LogLineContains("Starting listening for CQL clients on"))

  abstract override def dockerContainers: List[DockerContainer] = cassandraContainer :: super.dockerContainers
}
