package io.eels

import com.sksamuel.exts.Logging
import io.eels.dataframe.{DataStream, ExecutionManager}
import io.eels.schema.StructType
import io.eels.util.JacksonSupport
import io.reactivex.functions.Consumer
import io.reactivex.{Emitter, Flowable}

/**
  * A Source is a provider of data.
  *
  * A source implementation must provide two methods:
  *
  * 1: schema() which returns an eel schema for the data source.
  *
  * 2: parts() which returns zero or more Part instances representing the data.
  *
  * A part instance is a subset of the data in a Source, and allows for concurrent
  * reading of that data. For example a part could be a single file in a multi-file source, or
  * a partition in a partitioned source.
  */
trait Source extends Logging {
  outer =>

  def schema: StructType

  def parts(): Seq[Part]

  def load[T: Manifest](): Seq[T] = {
    toFrame().toSeq().map { row =>
      val node = JsonRow(row)
      JacksonSupport.mapper.readerFor[T].readValue(node)
    }
  }

  def toFrame(): Frame = toFrame(NoopListener)
  def toFrame(_listener: Listener): Frame = new SourceFrame(this, _listener)

  def toDataStream(): DataStream = toDataStream(NoopListener)
  def toDataStream(listener: Listener): DataStream = new DataStream {
    override def schema: StructType = outer.schema
    override private[eels] def partitions(implicit em: ExecutionManager) = outer.parts().map { part =>
      val iterator = Iterator.apply(part.iterator.toList.flatten: _*)
      Flowable.generate(new Consumer[Emitter[Row]] {
        override def accept(t: Emitter[Row]): Unit = {
          if (iterator.hasNext) t.onNext(iterator.next)
          else t.onComplete()
        }
      })
    }
  }

}