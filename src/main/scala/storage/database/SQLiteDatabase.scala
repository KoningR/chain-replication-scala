package storage.database

import slick.jdbc.SQLiteProfile.api._
import storage.Database

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class Objects(tag: Tag) extends Table[(Int, String)](tag, "OBJECTS") {
  def id = column[Int]("OBJECT_ID", O.PrimaryKey)
  def json_blob = column[String]("JSON_BLOB")
  def * = (id, json_blob)
}

class SQLiteDatabase(fileName: String) extends Database {
  // Initializations of database and table
  val objects = TableQuery[Objects]
  val db = Database.forConfig(fileName)

  val setup = DBIO.seq(
    objects.schema.createIfNotExists
  )
  val setupFuture: Unit = Await.result(db.run(setup), 20 seconds)

  override def get(objectId: Int): Future[Option[String]] = {
    val action = objects
      .filter(_.id === objectId)
      .map(_.json_blob)
      .result
      .headOption
    db.run(action)
  }

  override def upsert(objectId: Int, value: String): Future[Option[String]] = {
    val action = objects.insertOrUpdate((objectId, value))

    // The result of the method is the amount of rows it has changed,
    // if it is 1 it was successful and we assume that the value given
    // is inserted correctly, which we return again in a future.
    db.run(action).map {
      case 1 => Some(value)
      case _ => None
    }
  }

  override def close(): Unit = db.close()
}
