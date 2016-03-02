package io.eels.component.hive

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.eels.{InternalRow, Schema, Sink, SinkWriter}
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.hive.conf.HiveConf
import org.apache.hadoop.hive.metastore.HiveMetaStoreClient

import scala.collection.JavaConverters._

case class HiveSink(private val dbName: String,
                    private val tableName: String,
                    private val ioThreads: Int = 4,
                    private val dynamicPartitioning: Boolean = true)
                   (implicit fs: FileSystem, hiveConf: HiveConf) extends Sink with StrictLogging {

  val config = ConfigFactory.load()
  val includePartitionsInData = config.getBoolean("eel.hive.includePartitionsInData")
  val bufferSize = config.getInt("eel.hive.bufferSize")

  def withIOThreads(ioThreads: Int): HiveSink = copy(ioThreads = ioThreads)
  def withDynamicPartitioning(dynamicPartitioning: Boolean): HiveSink = copy(dynamicPartitioning = dynamicPartitioning)

  private def hiveSchema(implicit client: HiveMetaStoreClient): Schema = {
    val schema = client.getSchema(dbName, tableName)
    HiveSchemaFns.fromHiveFields(schema.asScala)
  }

  private def dialect(implicit client: HiveMetaStoreClient): HiveDialect = {
    val format = HiveOps.tableFormat(dbName, tableName)
    logger.debug(s"Table format is $format")
    HiveDialect(format)
  }

  override def writer(schema: Schema): SinkWriter = {

    implicit val client = new HiveMetaStoreClient(hiveConf)

    new HiveSinkWriter(
      schema,
      hiveSchema,
      dbName,
      tableName,
      ioThreads,
      dialect,
      dynamicPartitioning,
      includePartitionsInData,
      bufferSize
    )
  }
}

object HiveSink {

  @deprecated("functionality should move to the HiveSinkBuilder", "0.33.0")
  def apply(dbName: String, tableName: String, params: Map[String, List[String]])
           (implicit fs: FileSystem, hiveConf: HiveConf): HiveSink = {
    val dynamicPartitioning = params.get("dynamicPartitioning").map(_.head).getOrElse("false") == "true"
    HiveSink(
      dbName,
      tableName,
      dynamicPartitioning = dynamicPartitioning
    )
  }
}

// returns all the partition parts for a given row, if a row doesn't contain a value
// for a part then an error is thrown
object RowPartitionParts {
  def apply(row: InternalRow, partNames: Seq[String], schema: Schema): List[PartitionPart] = {
    require(partNames.forall(schema.columnNames.contains), "Schema must contain all partitions " + partNames)
    partNames.map { name =>
      val index = schema.indexOf(name)
      val value = row(index)
      require(!value.toString.contains(" "), s"Values for partitions cannot contain spaces $name=$value (index $index)")
      PartitionPart(name, value.toString)
    }.toList
  }
}