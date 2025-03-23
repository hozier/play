package com.theproductcollectiveco.play4s.game.sudoku.parser

import cats.effect._
import io.circe._
import io.circe.parser._
import smithy4s.Blob
import cats.syntax.either._
import scala.io.Source
import java.util.Base64
import com.theproductcollectiveco.play4s.game.sudoku.ComputeRequest

/**
  * Motivation: Validate server-side decoding of an image in Base64.
  * This module serves only as a testable component to the overall expected compute flow--
  * largely mimicking functionality which smithy4s obsures when a request is made.
  * 
  * Blob: Represents binary data (byte array) of the image.
  * Base64: Encoding format used to represent binary data as a string.
  * Process: The client sends a Base64-encoded image in a JSON payload. The server decodes the Base64 string into a Blob, processes the image data, and computes the Sudoku solution.
  */
object ImagePayload {
  def apply(imageData: Blob): ComputeRequest = new ComputeRequest   {
    
  override def image: Blob = imageData
    
    // Custom Decoder to directly decode base64 into Blob
  implicit val decoder: Decoder[ComputeRequest] = Decoder.instance { cursor =>
    cursor.get[String]("image").flatMap { base64Str =>
      Either.catchNonFatal {
        val bytes = Base64.getDecoder.decode(base64Str)
        ImagePayload(Blob(bytes))
      }.leftMap(e => DecodingFailure(s"Base64 decoding failed: ${e.getMessage}", cursor.history))
    }
  }

  def readJsonFile[F[_]: Async](filePath: String): F[String] = Async[F].delay {
    val source = Source.fromResource(filePath)
    try source.mkString finally source.close()
  }

  def decodeImagePayload[F[_]: Async](json: String): F[ComputeRequest] =
    Async[F].fromEither(parse(json).flatMap(_.as[ComputeRequest]))
  }
}
