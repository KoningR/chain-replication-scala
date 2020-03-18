package storage.database

import storage.Database

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * The InMemoryMapDatabase can be used for testing quickly, as it relies on a map instead of a database file.
 */
class InMemoryMapDatabase extends Database {

    var storage: Map[Int, String] = Map()

    override def get(objectId: Int): Future[Option[String]] = Future {
        storage.get(objectId)
    }

    override def upsert(objectId: Int, value: String): Future[Option[String]] = Future {
        storage = storage.updated(objectId, value)
        Some(value)
    }

    override def close(): Unit = {}
}
