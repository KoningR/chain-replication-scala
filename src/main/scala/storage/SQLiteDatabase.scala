package storage

import slick.jdbc.SQLiteProfile.api._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class Objects(tag: Tag) extends Table[(Int, String)](tag, "OBJECTS") {
  def id = column[Int]("OBJECT_ID", O.PrimaryKey)

  def json_blob = column[String]("JSON_BLOB")

  def * = (id, json_blob)
}

class SQLiteDatabase(fileName: String) {
  val objects = TableQuery[Objects]
  val db = Database.forConfig(fileName)

  val setup = DBIO.seq(
    objects.schema.createIfNotExists
  )

  val setupFuture: Unit = Await.result(db.run(setup), 20 seconds)

  def getById(objectId: Int): Future[Option[(Int, String)]] = {
    db.run(objects.filter(_.id === objectId).result.headOption)
  }
}
