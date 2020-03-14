package storage

import ujson.Value.Value

class Storage {

  var storage: Map[Int, String] = Map()

  def query(objectId: Int, options: Option[List[String]] = None): Option[String] = {
    // Get entry from source.
    val databaseEntry: Option[String] = storage.get(objectId)

    databaseEntry match {
      case Some(jsonStringAllFields) =>
        // Read the string entry into JSON.
        val json: Value = ujson.read(jsonStringAllFields).obj
        val jsonObject = json.obj

        // Stop if the JSON is not an object.
        if (!json.isInstanceOf[ujson.Obj]) {
          return None
        }

        // Apply options (fields) to return.
        options match {
          case Some(options) =>
            // Fields given, return only requested fields.
            // TODO: make this more efficient
            val filteredObj = jsonObject.filter(x => options.contains(x._1))
            val jsonString = ujson.write(filteredObj)
            Some(jsonString)
          case None =>
            // No options given, return all fields.
            val jsonString = ujson.write(jsonObject)
            Some(jsonString)
        }
      case None => None
    }
  }

  def update(objectId: Int, newValue: String, options: Option[List[String]] = None): Option[String] = {

    // Get entry from source.
    val databaseEntry = storage.get(objectId)

    // Parse object that the client sent.
    val jsonObject = ujson.read(newValue)

    // Stop if the JSON is not an object.
    if (!jsonObject.isInstanceOf[ujson.Obj]) {
      return None
    }

    databaseEntry match {
      case Some(previousValue) =>
        // Object present, update.
        // TODO: interpret options and non-deterministic update
        val jsonStringUpdated = ujson.write(jsonObject)
        storage = storage.updated(objectId, jsonStringUpdated)

        Some(jsonStringUpdated)
      case None =>
        // No object present, create.
        val jsonStringUpdated = ujson.write(jsonObject)
        storage = storage.updated(objectId, jsonStringUpdated)

        Some(jsonStringUpdated)
    }
  }


}
