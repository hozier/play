package com.theproductcollectiveco.play4s

import cats.effect.IO
import weaver.SimpleIOSuite
import com.theproductcollectiveco.play4s.game.sudoku.parser.GoogleCloudClient
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.play4s.shared.Mocks
import weaver.*
import cats.syntax.all.*
import com.theproductcollectiveco.play4s.config.AppConfig

object GoogleCloudClientSpec extends SimpleIOSuite {
  given Logger[IO] = Slf4jLogger.getLogger[IO]

  test("GoogleCloudClient should correctly parse a Sudoku image") {
    for {
      onCI            <- AppConfig.load[IO].map(_.runtime.onCI)
      _               <- ignore("Skip call outs to Google Cloud API on CI").whenA(onCI)
      expectedState    = Mocks.initialState
      parser           = GoogleCloudClient[IO]
      imageBytes      <- parser.fetchBytes("sudoku_test_image_v0.0.1.png")
      serializedBoard <- parser.parseImage(imageBytes)
      _               <-
        Logger[IO].info:
          s"result: $serializedBoard"
    } yield expect(serializedBoard == expectedState.map(_.mkString("")).mkString(""))
  }

}
