package csw.services.icd

import java.io._
import java.net.URI

import com.fasterxml.jackson.databind.JsonNode
import com.github.fge.jackson.JsonLoader
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration
import com.github.fge.jsonschema.core.load.download.URIDownloader
import com.github.fge.jsonschema.core.report.ProcessingReport
import com.github.fge.jsonschema.main.{JsonSchema, JsonSchemaFactory}
import com.typesafe.config.{Config, ConfigResolveOptions, ConfigFactory, ConfigRenderOptions}

import scala.io.Source

/**
 * An ICD validator
 */
object IcdValidator {

  /**
   * Returns a string with the contents of the given file, converted to JSON, if it was not already.
   * JSON files are recognized by the file suffix .json.
   * @param file a file in HOCON or JSON format
   * @return the file contents in JSON format
   */
  def toJson(file: File): String = {
    if (!file.exists()) throw new FileNotFoundException(file.getName)
    if (file.getName.endsWith(".json")) {
      Source.fromFile(file).mkString
    } else {
      val config = ConfigFactory.parseFile(file).resolve(ConfigResolveOptions.noSystem())
      toJson(config)
    }
  }

  val jsonOptions = ConfigRenderOptions.defaults().setComments(false).setOriginComments(false)

  /**
   * Returns a string with the contents of the given config, converted to JSON.
   * @param config the config to convert
   * @return the config contents in JSON format
   */
  def toJson(config: Config): String = {
    config.root.render(jsonOptions)
  }

  /**
   * Describes any validation problems found
   * @param severity a string describing the error severity: fatal, error, warning, etc.
   * @param message describes the problem
   * @param json additional information about the problem in JSON format
   */
  case class Problem(severity: String, message: String, json: String)

  // Adds a custom URI scheme, so that config:/... loads the config file as a resource
  // and converts it to JSON. In this way you can use "$ref": "config:/myfile.conf"
  // to refer to external JSON schemas in HOCON format.
  private case object ConfigDownloader extends URIDownloader {
    override def fetch(uri: URI): InputStream = {
      val config = ConfigFactory.parseResources(uri.getPath.substring(1))
      if (config == null) throw new IOException(s"Resource not found: ${uri.getPath}")
      new ByteArrayInputStream(toJson(config).getBytes)
    }
  }

  private val cfg = LoadingConfiguration.newBuilder.addScheme("config", ConfigDownloader).freeze
  private val factory = JsonSchemaFactory.newBuilder.setLoadingConfiguration(cfg).freeze

  /**
   * Validates the given input file using the given JSON schema file
   * JSON files are recognized by the file suffix .json.
   * @param inputFile a file in HOCON or JSON format
   * @param schemaFile a JSON schema file in HOCON or JSON format
   * @return a list of problems, if any were found
   */
  def validate(inputFile: File, schemaFile: File): List[Problem] = {
    val jsonSchema = JsonLoader.fromString(toJson(schemaFile))
    val schema = factory.getJsonSchema(jsonSchema)
    val jsonInput = JsonLoader.fromString(toJson(inputFile))
    validate(schema, jsonInput)
  }

  /**
   * Validates the given input config using the given schema config.
   * @param inputConfig the config to be validated against the schema
   * @param schemaConfig a config using the JSON schema syntax (but may be simplified to HOCON format)
   * @return a list of problems, if any were found
   */
  def validate(inputConfig: Config, schemaConfig: Config): List[Problem] = {
    val jsonSchema = JsonLoader.fromString(toJson(schemaConfig))
    val schema = factory.getJsonSchema(jsonSchema)
    val jsonInput = JsonLoader.fromString(toJson(inputConfig))
    validate(schema, jsonInput)
  }

  // Runs the validation and handles any internal exceptions
  private def validate(schema: JsonSchema, jsonInput: JsonNode): List[Problem] = {
    try {
      validateResult(schema.validate(jsonInput, true))
    } catch {
      case e: Exception ⇒
        e.printStackTrace()
        List(Problem("fatal", e.toString, ""))
    }
  }

  // Packages the validation results for return to caller
  private def validateResult(report: ProcessingReport): List[Problem] = {
    import scala.collection.JavaConverters._
    val result = for (msg ← report.asScala)
    yield Problem(msg.getLogLevel.toString, msg.getMessage,
        toJson(ConfigFactory.parseString(msg.asJson().toString)))
    result.toList
  }
}