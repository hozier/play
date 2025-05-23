package com.theproductcollectiveco.play4s.tools

import cats.Show
import cats.effect.{Async, Clock, IO}
import cats.effect.std.{SecureRandom, UUIDGen}
import cats.syntax.all.*
import com.theproductcollectiveco.play4s.Metrics
import com.theproductcollectiveco.play4s.auth.*
import com.theproductcollectiveco.play4s.config.AppConfig
import com.theproductcollectiveco.play4s.game.sudoku.BoardState
import com.theproductcollectiveco.play4s.game.sudoku.core.{BacktrackingAlgorithm, Orchestrator, Search}
import com.theproductcollectiveco.play4s.game.sudoku.parser.{GoogleCloudClient, TraceClient}
import com.theproductcollectiveco.play4s.internal.auth.{GenericHandle, Token, Username}
import fs2.io.file.Files
import org.scalacheck.Gen
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver.{SimpleIOSuite, *}

import scala.annotation.unused

object SpecKit {

  object Tasks:

    def setupAuthProvider[F[_]: Async: Logger: Files](appConfig: AppConfig) =
      for {
        authProvider <- DefaultKeyStoreBackend[F].pure.flatMap(DefaultAuthProvider[F])
        _            <-
          appConfig.apiKeyStore.keyStoreManagement.credentialsFilePath
            .liftTo[F](new IllegalArgumentException("Missing credentials file path"))
            .flatMap {
              authProvider
                .storeCredentials(
                  appConfig.apiKeyStore.keyStoreManagement.key,
                  _,
                )
                .use_
            }
      } yield authProvider

    def requestTestToken[F[_]: Async: Clock: UUIDGen](
      jwtProvider: JwtProvider[F]
    ): F[Token] =
      SecureRandom.javaSecuritySecureRandom[F].flatMap { security =>
        given SecureRandom[F] = security
        UUIDGen.fromSecureRandom.randomUUID.map(_.toString).flatMap { uuid =>
          Clock[F].realTimeInstant.flatMap { now =>
            jwtProvider.generateJwt(
              handle = GenericHandle.username(Username("test-user-id")),
              expiration = now.getEpochSecond + 3600,
              issuedAt = now.getEpochSecond,
              roles = "anonymous" :: Nil,
              tokenId = uuid,
              additionalClaims = Map("env" -> "test").some,
              oneTimeUse = false,
              issuer = "app.play4s-service.io",
            )
          }
        }
      }

  end Tasks

  object Fixtures:

    val initialBoardState =
      Vector(
        Vector(5, 3, 0, 0, 7, 0, 0, 0, 0),
        Vector(6, 0, 0, 1, 9, 5, 0, 0, 0),
        Vector(0, 9, 8, 0, 0, 0, 0, 6, 0),
        Vector(8, 0, 0, 0, 6, 0, 0, 0, 3),
        Vector(4, 0, 0, 8, 0, 3, 0, 0, 1),
        Vector(7, 0, 0, 0, 2, 0, 0, 0, 6),
        Vector(0, 6, 0, 0, 0, 0, 2, 8, 0),
        Vector(0, 0, 0, 4, 1, 9, 0, 0, 5),
        Vector(0, 0, 0, 0, 8, 0, 0, 7, 9),
      )

    val updatedBoardState =
      Vector(
        Vector(5, 3, 4, 6, 7, 8, 9, 1, 2),
        Vector(6, 7, 2, 1, 9, 5, 3, 4, 8),
        Vector(1, 9, 8, 3, 4, 2, 5, 6, 7),
        Vector(8, 5, 9, 7, 6, 1, 4, 2, 3),
        Vector(4, 2, 6, 8, 5, 3, 7, 9, 1),
        Vector(7, 1, 3, 9, 2, 4, 8, 5, 6),
        Vector(9, 6, 1, 5, 3, 7, 2, 8, 4),
        Vector(2, 8, 7, 4, 1, 9, 6, 3, 5),
        Vector(3, 4, 5, 2, 8, 6, 1, 7, 9),
      )

  object Generators {

    import SharedInstances.given
    val searchGen: Gen[Search] = Gen.const(Search.make)

    val orchestratorGen =
      Gen.const {
        val traceParser = TraceClient[IO]
        val imageParser = GoogleCloudClient[IO]
        Orchestrator.make[IO](traceParser, imageParser)
      }

    /*
     - boardGen: "Maybe solvable" puzzles
     - Randomly removes 20–50 values from a known solution, without verifying that the resulting board is solvable.
     - Use: Fast tests like parsing/formatting; may yield unsolvable or ambiguous boards.

     - solvableBoardGen: Guaranteed solvable puzzles
     - Same logic, but checks if the state is solvable using BacktrackingAlgorithm.
     - Use: Slow but safe for testing solving logic; always returns a solvable board.
     */

    val boardGen: Gen[BoardState] =
      for {
        removalCount  <- Gen.chooseNum(20, 50)
        cellsToRemove <-
          Gen.pick(
            removalCount,
            for {
              row <- 0 until 9
              col <- 0 until 9
            } yield (row, col),
          )
      } yield {
        val state = Fixtures.updatedBoardState.map(_.toArray).toArray
        cellsToRemove.foreach { case (row, col) => state(row)(col) = 0 }
        BoardState(state.map(_.toVector).toVector)
      }

    val solvableBoardGen: Gen[BoardState] =
      Gen.delay {
        val search     = Search.make
        val algorithm  = BacktrackingAlgorithm.Operations
        val maxRetries = 5

        def createPuzzle(removalCount: Int): BoardState = {
          val cells =
            scala.util.Random
              .shuffle((for {
                row <- 0 until 9
                col <- 0 until 9
              } yield (row, col)).toList)
              .take(removalCount)

          val state = Fixtures.updatedBoardState.map(_.toArray).toArray
          cells.foreach { case (row, col) => state(row)(col) = 0 }
          BoardState(state.map(_.toVector).toVector)
        }

        LazyList
          .continually:
            createPuzzle(scala.util.Random.between(20, 50))
          .map: state =>
            Option.when(
              algorithm.loop(state, search, search.fetchEmptyCells(state), (1 to state.value.size).toList).isDefined
            )(state)
          .collectFirst { case Some(valid) => valid }
          .take(maxRetries)
          .toSeq
          .headOption match {
          case Some(board) => Gen.const(board)
          case None        => Gen.fail
        }
      }

  }

  object Operations extends SimpleIOSuite:

    def skipOnCI: IO[Unit] =
      AppConfig.load[IO].toResource.use { config =>
        IO.whenA(config.runtime.onCI.contains(true)) {
          ignore("Skip call outs to Google Cloud API on CI")
        }
      }

  object SharedInstances:
    // Define Show instances for Logger, Metrics, and Orchestrator to resolve forall issues
    given Logger[IO]             = Slf4jLogger.getLogger[IO]
    given IO[Metrics[IO]]        = Metrics.make[IO]
    given Show[Logger[IO]]       = Show.show(_ => "Logger[IO]")
    given Show[Metrics[IO]]      = Show.show(_ => "Metrics[IO]")
    given Show[Orchestrator[IO]] = Show.show(_ => "Orchestrator[IO]")
    given Show[BoardState]       = Show.show(_.toString)
    given Show[Search]           = Show.show(_.toString)

    def withLoggerAndMetrics[A](logger: Logger[IO], metrics: Metrics[IO])(f: => IO[A]): IO[A] = {
      @unused
      given Logger[IO]  = logger
      @unused
      given Metrics[IO] = metrics
      f
    }

}
