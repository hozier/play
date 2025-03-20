package com.theproductcollectiveco.test

import cats.effect.IO
import weaver.SimpleIOSuite
import com.theproductcollectiveco.game.sudoku.parser.GoogleCloudVisionClient
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.test.shared.Models

object GoogleCloudVisionClientSpec extends SimpleIOSuite {
  given Logger[IO] = Slf4jLogger.getLogger[IO]

  test("GoogleCloudVisionClient should correctly parse a Sudoku image") {
    val expectedBoardData = Models.expectedBoardData
    val parser            = GoogleCloudVisionClient[IO]

    for {
      imageBytes      <- parser.fetchBytes("sudoku_test_image_v0.0.1.png")
      serializedBoard <- parser.parseImage(imageBytes)
      _               <-
        Logger[IO].info:
          s"result: $serializedBoard"
    } yield expect(serializedBoard == expectedBoardData.map(_.mkString("")).mkString(""))
  }

}
