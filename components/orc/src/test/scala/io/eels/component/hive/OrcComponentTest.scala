package io.eels.component.hive

import io.eels.{Frame, Row}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
import org.scalatest.{Matchers, WordSpec}

class OrcComponentTest extends WordSpec with Matchers {

  "OrcComponent" should {
    "read and write orc files" in {

      implicit val fs = FileSystem.get(new Configuration)

      val frame = Frame(
        Row(Seq("name", "job", "location"), Seq("clint eastwood", "actor", "carmel")),
        Row(Seq("name", "job", "location"), Seq("elton john", "musician", "pinner")),
        Row(Seq("name", "job", "location"), Seq("david bowie", "musician", "surrey"))
      )

      val path = new Path("test.orc")
      frame.to(OrcSink(path))

      val rows = OrcSource(path).toList
      fs.delete(path, false)

      rows.map(_.fields.map(_.value)) shouldBe Seq(
        Seq("clint eastwood", "actor", "carmel"),
        Seq("elton john", "musician", "pinner"),
        Seq("david bowie", "musician", "surrey")
      )

    }
  }
}