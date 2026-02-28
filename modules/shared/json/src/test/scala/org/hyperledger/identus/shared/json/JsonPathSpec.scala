package org.hyperledger.identus.shared.json

import org.hyperledger.identus.shared.json.JsonPathError.{InvalidPathInput, PathNotFound}
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.test.*
import zio.test.Assertion.*

object JsonPathSpec extends ZIOSpecDefault {

  override def spec = suite("JsonPathSpec")(
    test("sucessfully compile a valid json path") {
      val paths = Seq(
        "$.store.book[*].author",
        "$..author",
        "$.store.*",
        "$.store..price",
        "$..book[2]",
        "$..book[-1:]",
        "$..book[0,1]",
        "$..book[:2]",
        "$..book[?(@.isbn)]",
        "$..book[?(@.price<10)]",
        "$..book[?(@.price==8.95)]",
        "$..book[?(@.price<30 && @.category==\"fiction\")]",
        "$..*",
        "$['foo']['bar']"
      )
      ZIO
        .foreach(paths)(p => ZIO.fromEither(JsonPath.compile(p)))
        .as(assertCompletes)
    },
    test("do not accept invalid json path") {
      val paths = Seq(
        "",
        " ",
        " $ ",
        "$$",
        "hello world",
      )
      ZIO
        .foreach(paths)(p => ZIO.fromEither(JsonPath.compile(p)).flip)
        .map { errors =>
          assert(errors)(forall(isSubtype[InvalidPathInput](anything)))
        }
    },
    test("query valid path inside json structure") {
      val jsonStr =
        """{
          |  "vc": {
          |    "name": "alice",
          |    "age": 42,
          |    "degree": null,
          |    "pets": ["dog", "cat"],
          |    "isEmployed": false,
          |    "languages": {"english": "native", "chinese": "fluent"}
          |  }
          |}
        """.stripMargin
      for {
        json <- ZIO.fromEither(jsonStr.fromJson[Json])
        namePath <- ZIO.fromEither(JsonPath.compile("$.vc.name"))
        agePath <- ZIO.fromEither(JsonPath.compile("$.vc.age"))
        degreePath <- ZIO.fromEither(JsonPath.compile("$.vc.degree"))
        petPath <- ZIO.fromEither(JsonPath.compile("$.vc.pets"))
        firstPetPath <- ZIO.fromEither(JsonPath.compile("$.vc.pets[0]"))
        isEmployedPath <- ZIO.fromEither(JsonPath.compile("$.vc.isEmployed"))
        languagesPath <- ZIO.fromEither(JsonPath.compile("$.vc.languages"))
        name <- ZIO.fromEither(namePath.read(json))
        age <- ZIO.fromEither(agePath.read(json))
        degree <- ZIO.fromEither(degreePath.read(json))
        pet <- ZIO.fromEither(petPath.read(json))
        firstPet <- ZIO.fromEither(firstPetPath.read(json))
        isEmployed <- ZIO.fromEither(isEmployedPath.read(json))
        languages <- ZIO.fromEither(languagesPath.read(json))
      } yield assert(name.asString)(isSome(equalTo("alice")))
        && assert(age.asNumber)(isSome(equalTo(Json.Num(42))))
        && assert(degree.asNull)(isSome(anything))
        && assert(pet.asArray)(isSome(hasSize((equalTo(2)))))
        && assert(firstPet.asString)(isSome(equalTo("dog")))
        && assert(isEmployed.asBoolean)(isSome(isFalse))
        && assert(languages.asObject)(isSome(anything))
    },
    test("query invalid path inside json structure") {
      val jsonStr =
        """{
          |  "vc": {
          |    "name": "alice",
          |    "pets": ["dog", "cat"]
          |  }
          |}
        """.stripMargin
      for {
        json <- ZIO.fromEither(jsonStr.fromJson[Json])
        nonExistingPath <- ZIO.fromEither(JsonPath.compile("$.vc2.name"))
        invalidTypeArrayPath <- ZIO.fromEither(JsonPath.compile("$.vc.name[0]"))
        outOfBoundArrayPath <- ZIO.fromEither(JsonPath.compile("$.vc.name[5]"))
        outOfBoundSlicePath <- ZIO.fromEither(JsonPath.compile("$.vc.name[1:4]"))
        exit1 <- ZIO.fromEither(nonExistingPath.read(json)).exit
        exit2 <- ZIO.fromEither(invalidTypeArrayPath.read(json)).exit
        exit3 <- ZIO.fromEither(outOfBoundArrayPath.read(json)).exit
        exit4 <- ZIO.fromEither(outOfBoundSlicePath.read(json)).exit
      } yield assert(exit1)(failsWithA[PathNotFound])
        && assert(exit2)(failsWithA[PathNotFound])
        && assert(exit3)(failsWithA[PathNotFound])
        && assert(exit4)(failsWithA[PathNotFound])
    }
  )

}
