package com.theproductcollectiveco.play4s

import cats.effect.IO
import weaver.SimpleIOSuite
import com.theproductcollectiveco.play4s.game.sudoku.parser.GoogleCloudClient
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.play4s.SpecKit.Fixtures.*
import com.theproductcollectiveco.play4s.SpecKit.Operations.*
import weaver.*

object GoogleCloudClientSpec extends SimpleIOSuite {
  given Logger[IO] = Slf4jLogger.getLogger[IO]

  test("GoogleCloudClient should correctly parse a Sudoku image") {
    for {
      _               <- skipOnCI
      parser           = GoogleCloudClient[IO]
      imageBytes      <- parser.fetchBytes("sudoku_test_image_v0.0.1.png")
      serializedBoard <- parser.parseImage(imageBytes)
      _               <-
        Logger[IO].debug:
          s"result: $serializedBoard"
    } yield expect(serializedBoard == initialBoardState.map(_.mkString).mkString)
  }

}
