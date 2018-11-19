package co.wishkeeper.server.messaging

import org.fusesource.scalate.TemplateEngine
import org.fusesource.scalate.util.Resource

import scala.io.Source
import scala.util.Try

trait TemplateEngineAdapter {
  def process(templatePath: String, variables: Map[String, Any]): Try[String]
}

class ScalateTemplateEngine extends TemplateEngineAdapter {
  private val engine = new TemplateEngine
  engine.resourceLoader = (uri: String) => Try(Resource.fromSource(uri, Source.fromInputStream(getClass.getResourceAsStream(uri)))).toOption

  override def process(templatePath: String, variables: Map[String, Any]): Try[String] = Try {
    engine.layout(templatePath, variables)
  }
}