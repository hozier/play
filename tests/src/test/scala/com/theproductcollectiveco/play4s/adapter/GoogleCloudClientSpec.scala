package com.theproductcollectiveco.play4s.adapter

import cats.effect.IO
import com.theproductcollectiveco.play4s.game.sudoku.parser.GoogleCloudClient
import com.theproductcollectiveco.play4s.tools.SpecKit.Fixtures.*
import com.theproductcollectiveco.play4s.tools.SpecKit.Operations.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver.{SimpleIOSuite, *}

object GoogleCloudClientSpec extends SimpleIOSuite {
  given Logger[IO] = Slf4jLogger.getLogger[IO]

  test("GoogleCloudClient should correctly parse a Sudoku image") {
    for {
      _               <- skipOnCI
      parser           = GoogleCloudClient[IO]
      imageBytes      <- parser.fetchResourceBytes("sudoku_test_image_v0.0.1.png")
      serializedBoard <- parser.parseImage(imageBytes)
      _               <-
        Logger[IO].debug:
          s"result: $serializedBoard"
    } yield expect(serializedBoard == initialBoardState.map(_.mkString).mkString)
  }

}
