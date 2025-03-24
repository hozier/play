package com.theproductcollectiveco.play4s

import cats.effect.IO
import org.http4s._
import org.http4s.circe._
import io.circe.Json
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import smithy4s.Blob
import weaver.scalacheck.Checkers
import weaver.SimpleIOSuite

object MiddlewareSpec extends SimpleIOSuite with Checkers {

  given Logger[IO] = Slf4jLogger.getLogger[IO]

  test("decodeJsonToBlob should decode JSON content") {
    val json = Json.obj("image" -> Json.fromString("Y29tLnRoZXByb2R1Y3Rjb2xsZWN0aXZlY28ucGxheTRzLk1pZGRsZXdhcmVTcGVj")) 
    val req = Request[IO](method = Method.POST).withEntity(json)

    Middleware.decodeContent[IO](req).map { blob =>
      expect(blob.toArray.sameElements("com.theproductcollectiveco.play4s.MiddlewareSpec".getBytes))
    }
  }
}