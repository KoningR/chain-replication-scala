package storage

import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class SQLiteDatabaseTest extends FunSuite {

  test("should setup and return select statement that is empty") {
    val storageSqlite: SQLiteDatabase = new SQLiteDatabase("database_test.db")
    val result = Await.result(storageSqlite.getById(1), 20 seconds)

    result match {
      case Some(_) => fail()
      case None => succeed
    }

    storageSqlite.db.close()
  }
}
