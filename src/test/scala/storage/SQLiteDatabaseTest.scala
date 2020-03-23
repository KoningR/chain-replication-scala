package storage

import org.scalatest._
import storage.database.SQLiteDatabase

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class SQLiteDatabaseTest extends FunSuite {

    val fileName = "test"

    test("should setup and return select statement that is empty") {
        val storageSqlite: SQLiteDatabase = new SQLiteDatabase(fileName)
        val result = Await.result(storageSqlite.get(999), 20 seconds)

        result match {
            case Some(_) => fail()
            case None => succeed
        }

        storageSqlite.close()
    }

    test("multiple sqlite-database tests with 1 update") {
        val storageSqlite1: SQLiteDatabase = new SQLiteDatabase("test_1")
        val storageSqlite2: SQLiteDatabase = new SQLiteDatabase("test_2")
        val storageSqlite3: SQLiteDatabase = new SQLiteDatabase("test_3")
        val storageSqlite4: SQLiteDatabase = new SQLiteDatabase("test_4")

        storageSqlite1.clear()
        storageSqlite2.clear()
        storageSqlite3.clear()
        storageSqlite4.clear()

        Await.result(storageSqlite4.upsert(999, """{"name":"Akka"}"""), 20 seconds)

        val shouldBeFalse = List(
            Await.result(storageSqlite1.get(999), 20 seconds),
            Await.result(storageSqlite2.get(999), 20 seconds),
            Await.result(storageSqlite3.get(999), 20 seconds)
        ).exists(_.isDefined)

        val shouldBeTrue = Await.result(storageSqlite4.get(999), 20 seconds).isDefined

        storageSqlite1.close()
        storageSqlite2.close()
        storageSqlite3.close()
        storageSqlite4.close()

        if (!shouldBeFalse && shouldBeTrue) {
            succeed
        } else {
            fail()
        }

    }

    test("multiple sqlite-database tests with 2 updates") {
        val storageSqlite1: SQLiteDatabase = new SQLiteDatabase("test_1")
        val storageSqlite2: SQLiteDatabase = new SQLiteDatabase("test_2")

        storageSqlite1.clear()
        storageSqlite2.clear()

        Await.result(storageSqlite1.upsert(999, """{"name":"Akka1"}"""), 20 seconds)
        Await.result(storageSqlite2.upsert(999, """{"name":"Akka2"}"""), 20 seconds)

        val check1 = Await.result(storageSqlite1.get(999), 20 seconds).get == """{"name":"Akka1"}"""
        val check2 = Await.result(storageSqlite2.get(999), 20 seconds).get == """{"name":"Akka2"}"""

        storageSqlite1.close()
        storageSqlite2.close()

        if (check1 && check2) {
            succeed
        } else {
            fail()
        }

    }
}
