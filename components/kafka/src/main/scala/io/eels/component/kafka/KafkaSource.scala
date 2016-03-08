package io.eels.component.kafka

import java.util.{Properties, UUID}

import com.sksamuel.scalax.Logging
import io.eels._
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.serialization.ByteArrayDeserializer

import scala.collection.JavaConverters._

case class KafkaSourceConfig(brokerList: String,
                             consumerGroup: String,
                             autoOffsetReset: String = "earliest",
                             enableAutoCommit: Boolean = false)

case class KafkaSource(config: KafkaSourceConfig, topics: Set[String], deserializer: KafkaDeserializer)
  extends Source with Logging {

  override def schema: Schema = {

    val consumerProps = new Properties
    consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.brokerList)
    consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "schema-consumer_" + UUID.randomUUID)
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    val consumer = new KafkaConsumer[Array[Byte], Array[Byte]](
      consumerProps,
      new ByteArrayDeserializer,
      new ByteArrayDeserializer
    )
    consumer.subscribe(topics.toList.asJava)

    logger.debug("Polling kafka for schema")
    val record = consumer.poll(10000).asScala.take(1).toList.head
    consumer.close()
    val row = deserializer(record.value)
    val columns = List.tabulate(row.size)(_.toString)
    Schema(columns)
  }

  class KafkaPart extends Part {
    override def reader: SourceReader = new KafkaSourceReader
  }

  class KafkaSourceReader extends SourceReader {

    val consumerProps = new Properties
    consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.brokerList)
    consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, config.consumerGroup)
    consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, config.enableAutoCommit.toString)
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, config.autoOffsetReset)
    val consumer = new KafkaConsumer[Array[Byte], Array[Byte]](
      consumerProps,
      new ByteArrayDeserializer,
      new ByteArrayDeserializer
    )
    consumer.subscribe(topics.toList.asJava)

    val records = consumer.poll(10000).asScala.toList
    logger.debug(s"Read ${records.size} records from kafka")
    consumer.close()
    logger.debug("Closed kafka consumer")

    override def close(): Unit = ()
    override def iterator: Iterator[InternalRow] = records.iterator.map { record =>
      val bytes = record.value()
      deserializer(bytes)
    }
  }
  override def parts: Seq[Part] = Seq(new KafkaPart)
}


trait KafkaDeserializer {
  def apply(bytes: Array[Byte]): InternalRow
}

