package com.theproductcollectiveco.play4s.game.sudoku.parser

import cats.effect.{Async, Sync}
import cats.syntax.all.*
import org.http4s.client.Client
import org.http4s.{Header, Method, Request, Uri}
import scala.concurrent.duration.DurationInt
import org.typelevel.log4cats.Logger
import org.typelevel.ci.*
import retry.RetryPolicies.*
import retry.RetryDetails
import retry.*

trait SudokuExtractor[F[_]] {
  def fetchTxtRepresentation(puzzleNumber: Long): F[(String, String)]
}

object WebSudokuExtractor {

  def apply[F[_]: Async: Logger](client: Client[F]): SudokuExtractor[F] =
    new SudokuExtractor[F] {

      private val BaseUri = "https://west.websudoku.com/?level=1&set_id="

      override def fetchTxtRepresentation(puzzleNumber: Long): F[(String, String)] =
        for {
          _                                  <- validatePuzzleNumber(puzzleNumber)
          uri                                <- buildUri(puzzleNumber)
          html                               <- fetchHtml(uri)
          (solution, validatedStartingTrace) <- extractTxtFromHtml(html)
        } yield (solution, validatedStartingTrace)

      private def validatePuzzleNumber(n: Long): F[Unit] =
        Sync[F]
          .raiseError(new IllegalArgumentException("Puzzle number must be between 0 and 10,000,000,000"))
          .unlessA(n >= 0L && n <= 10000000000L)

      private def buildUri(puzzleNumber: Long): F[Uri] = Uri.fromString(s"$BaseUri$puzzleNumber").liftTo[F]

      private def fetchHtml(uri: Uri): F[String] =
        retryingOnAllErrors(
          policy = limitRetries[F](3) join exponentialBackoff[F](100.milliseconds),
          onError = (e: Throwable, details: RetryDetails) => Logger[F].warn(s"Retrying fetchHtml due to error: ${e.getMessage}, details: $details"),
        ) {
          client.expect[String](
            Request[F](Method.GET, uri).withHeaders(
              Header.Raw(ci"User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36"),
              Header.Raw(ci"Accept", "text/html"),
            )
          )
        }

      private def extractTxtFromHtml(html: String): F[(String, String)] =
        for {
          cheat  <- extractInputValue(html, "cheat", "cheat value")
          mask   <- extractInputValue(html, "editmask", "editmask value")
          result <- validateSolutionAndMask(cheat, mask)
        } yield result

      private def extractInputValue(html: String, name: String, errorMsg: String): F[String] =
        Seq(
          s"""(?i)var\\s+$name\\s*=\\s*['"]([0-9]{81})['"];""".r,
          s"""(?i)<input[^>]*\\b(?:id|name)=['"]?$name['"]?[^>]*\\bvalue=['"]?([0-9]{81})['"]?""".r,
          s"""(?i)<input[^>]*\\bvalue=['"]?([0-9]{81})['"]?[^>]*\\b(?:id|name)=['"]?$name['"]?""".r,
        ).view
          .flatMap(_.findFirstMatchIn(html).map(_.group(1)))
          .headOption
          .liftTo[F](new RuntimeException(s"Missing $errorMsg"))

      private def validateSolutionAndMask(cheat: String, mask: String): F[(String, String)] =
        (cheat, mask)
          .pure[F]
          .flatTap { case (c, m) =>
            Sync[F]
              .raiseError(new RuntimeException("Cheat and mask must each be 81 characters long"))
              .unlessA(c.length == 81 && m.length == 81)
          }
          .map { case (c, m) =>
            val validatedStartingTrace = c.zip(m).map { case (ch, mk) => if mk == '1' then ch else '0' }.mkString
            (c, validatedStartingTrace)
          }
    }

}
