package storage

class Storage {

  var storage: Map[Int, String] = _

  def init(): Unit = {
    storage = Map()
  }

  def query(objectId: Int, options: Option[List[String]] = None): String = {
    ""
  }

  def update(objectId: Int, newValue: String, options: Option[List[String]] = None): String = {
    ""
  }


}
