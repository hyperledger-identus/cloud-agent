package common.errors

import com.google.gson.Gson
import com.google.gson.JsonObject
import common.CredentialSchema.STUDENT_SCHEMA
import net.serenitybdd.screenplay.Actor

enum class SchemaTemplateError {
    TYPE_AND_PROPERTIES_WITHOUT_SCHEMA_TYPE {
        override fun innerSchema(): String = """
                {
                    "type": "object",
                    "properties": {
                        "name": {
                            "type": "string"
                        },
                        "age": {
                            "type": "integer"
                        }
                    },
                    "required": ["name"]
                }
        """.trimIndent()
    },
    CUSTOM_WORDS_NOT_DEFINED {
        override fun innerSchema(): String = """
                {
                  "${"$"}schema": "http://json-schema.org/draft-2020-12/schema#",
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string"
                    },
                    "age": {
                      "type": "integer"
                    }
                  },
                  "customKeyword": "value"
                }
        """.trimIndent()
    },
    MISSING_REQUIRED_FOR_MANDATORY_PROPERTY {
        override fun innerSchema(): String = """
            {
              "${"$"}schema": "http://json-schema.org/draft-2020-12/schema#",
              "type": "object",
              "properties": {
                "name": {
                  "type": "string"
                },
                "age": {
                  "type": "integer"
                }
              }
            }
            """
    }, ;

    abstract fun innerSchema(): String

    fun schema(actor: Actor): String {
        val innerSchema = Gson().fromJson(innerSchema(), JsonObject::class.java)
        val json = getJson(actor)
        json.add("schema", innerSchema)
        return json.toString()
    }

    private fun getJson(actor: Actor): JsonObject {
        val jsonString = Gson().toJson(STUDENT_SCHEMA.credentialSchema.copy(author = actor.recall("shortFormDid")))
        return Gson().fromJson(jsonString, JsonObject::class.java)
    }
}
