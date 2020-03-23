package storage

import org.scalatest._

class StorageTest extends FunSuite {

  val fileName = "test"

  val rawJson: String =
    """{"name":"Watership Down","location":{"lat":51.235685,"long":-1.309197},"residents":[{"name":"Fiver","age":4,"role":null},{"name":"Bigwig","age":6,"role":"Owsla"}]}"""

  val rawJsonNameFieldOnly: String =
    """{"name":"Watership Down"}"""

  val listJson: String =
    """[1, 2, 3, 4, 5]"""

  test("should initialize without errors") {
    new Storage(fileName)
  }

  test("should update object and receive it back successfully") {
    val storage = new Storage(fileName)
    val updateResult = storage.update(1, rawJson, None)

    updateResult match {
      case Some(jsonBack) =>
        assert(jsonBack == rawJson)
      case None => fail()
    }
  }

  test("should update object and receive it back successfully after query") {
    val storage = new Storage(fileName)
    storage.update(1, rawJson, None)

    val query = storage.query(1, None)

    query match {
      case Some(jsonBack) =>
        assert(jsonBack == rawJson)
      case None => fail()
    }
  }

  test("should only receive the given field after query") {
    val storage = new Storage(fileName)
    storage.update(1, rawJson, None)

    val options = Some(List("name"))
    val query = storage.query(1, options)

    query match {
      case Some(jsonBack) =>
        assert(jsonBack == rawJsonNameFieldOnly)
      case None => fail()
    }
  }

  test("should update two objects which are both defined") {
    val storage = new Storage(fileName)
    val updateResult = storage.update(1, rawJson, None)
    val updateResult2 = storage.update(2, rawJson, None)

    (updateResult, updateResult2) match {
      case (Some(jsonBack), Some(jsonBack2)) =>
        assert(jsonBack == rawJson && jsonBack2 == rawJson)
      case _ => fail()
    }
  }

  test("should deny any non object updates") {
    val storage = new Storage(fileName)
    val updatedResult = storage.update(1, listJson, None)

    updatedResult match {
      case Some(a) =>
        println(a)
        fail()
      case None => succeed
    }
  }

  test("should deny any non valid json updates") {
    val storage = new Storage(fileName)
    val updatedResult = storage.update(1, "{abc; 12}", None)

    updatedResult match {
      case Some(a) =>
        println(a)
        fail()
      case None => succeed
    }
  }

  test("multiple storage tests with 1 update") {
    val storage1: Storage = new Storage("test_storage_1")
    val storage2: Storage = new Storage("test_storage_2")
    val storage3: Storage = new Storage("test_storage_3")
    val storage4: Storage = new Storage("test_storage_4")

    storage1.storage.clear()
    storage2.storage.clear()
    storage3.storage.clear()
    storage4.storage.clear()

    storage4.update(999, """{"name":"Akka"}""", None)

    val shouldBeFalse = List(
      storage1.query(999, None),
      storage2.query(999, None),
      storage3.query(999, None)
    ).exists(_.isDefined)

    val shouldBeTrue = storage4.query(999, None).isDefined

    storage1.storage.close()
    storage2.storage.close()
    storage3.storage.close()
    storage4.storage.close()

    if (!shouldBeFalse && shouldBeTrue) {
      succeed
    } else {
      fail()
    }
  }
}

