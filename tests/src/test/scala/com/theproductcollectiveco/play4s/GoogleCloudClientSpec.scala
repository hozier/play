package com.theproductcollectiveco.play4s

import cats.effect.IO
import weaver.SimpleIOSuite
import com.theproductcollectiveco.play4s.game.sudoku.parser.GoogleCloudClient
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.play4s.shared.SpecKit.Fixtures
import weaver.*
import cats.syntax.all.*

object GoogleCloudClientSpec extends SimpleIOSuite {
  given Logger[IO] = Slf4jLogger.getLogger[IO]

  test("GoogleCloudClient should correctly parse a Sudoku image") {
    for {
      onCI            <- IO(sys.env.get("HOMEBREW_CELLAR").isEmpty)
      _               <- ignore("Skip call outs to Google Cloud API on CI").whenA(onCI)
      expectedState    = Fixtures.initialBoardState
      parser           = GoogleCloudClient[IO]
      imageBytes      <- parser.fetchBytes("sudoku_test_image_v0.0.1.png")
      serializedBoard <- parser.parseImage(imageBytes)
      _               <-
        Logger[IO].info:
          s"result: $serializedBoard"
    } yield expect(serializedBoard == expectedState.map(_.mkString("")).mkString(""))
  }

}
