package com.theproductcollectiveco.play4s.game.sudoku.parser

import com.theproductcollectiveco.play4s.game.sudoku.shared.Parser
import cats.effect.Async
import com.google.cloud.vision.v1.{ImageAnnotatorClient, Image, Feature, AnnotateImageRequest, TextAnnotation}
import com.google.protobuf.ByteString
import scala.jdk.CollectionConverters.*

trait ImageParser[F[_]] extends Parser[F] {
  def parseImage(image: Array[Byte]): F[String]
}

object GoogleCloudClient {

  def apply[F[_]: Async]: ImageParser[F] =
    new ImageParser[F] with Parser[F] {

      override def parseImage(image: Array[Byte]): F[String] =
        Async[F].delay {
          val visionClient = ImageAnnotatorClient.create()
          val imgBytes     = ByteString.copyFrom(image)
          val img          = Image.newBuilder().setContent(imgBytes).build()
          val feature      = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build()
          val request      = AnnotateImageRequest.newBuilder().addFeatures(feature).setImage(img).build()
          val response     = visionClient.batchAnnotateImages(java.util.Arrays.asList(request))
          val annotation   = response.getResponsesList.get(0).getFullTextAnnotation

          // Use the positional data to robustly parse the board.
          val board = parseOCRAnnotation(annotation)
          visionClient.close()
          board
        }

      /**
       * This method uses the hierarchical structure and bounding box information of the OCR result to assign detected digits to a cell within a 9×9
       * grid.
       */
      private def parseOCRAnnotation(annotation: TextAnnotation): String = {
        // Extract symbols along with their centroid positions.
        // We treat digits as valid, and map dots or spaces to a zero.
        val digitSymbols =
          for {
            page      <- annotation.getPagesList.asScala
            block     <- page.getBlocksList.asScala
            paragraph <- block.getParagraphsList.asScala
            word      <- paragraph.getWordsList.asScala
            symbol    <- word.getSymbolsList.asScala
            char       = symbol.getText.charAt(0)
            if char.isDigit || char == '.' || char == ' '
          } yield {
            // Convert '.' or ' ' into '0'
            val digit    = if char.isDigit then char else '0'
            // Compute the centroid of the symbol's bounding polygon.
            val vertices = symbol.getBoundingBox.getVerticesList.asScala
            val centerX  = vertices.map(_.getX).sum.toFloat / vertices.size
            val centerY  = vertices.map(_.getY).sum.toFloat / vertices.size
            (digit, centerX, centerY)
          }

        // Fallback: If no positional symbols were found, use a simple filter of the text.
        if digitSymbols.isEmpty then {
          val filtered = annotation.getText.filter(c => c.isDigit || c == '.' || c == ' ')
          val cells    =
            filtered.map {
              case d if d.isDigit => d
              case _              => '0'
            }
          return cells.padTo(81, '0').mkString.take(81)
        }

        // Determine the bounding box that contains all detected digits.
        val minX = digitSymbols.map(_._2).min
        val maxX = digitSymbols.map(_._2).max
        val minY = digitSymbols.map(_._3).min
        val maxY = digitSymbols.map(_._3).max

        // Calculate each cell's dimensions.
        val cellWidth  = (maxX - minX) / 9.0f
        val cellHeight = (maxY - minY) / 9.0f

        // Initialize a 9x9 board filled with '0's.
        val board = Array.fill(9, 9)('0')

        // For each detected digit, compute its grid cell using its centroid.
        for (digit, centerX, centerY) <- digitSymbols do {
          val col = ((centerX - minX) / cellWidth).toInt
          val row = ((centerY - minY) / cellHeight).toInt
          // Clamp the row and column indexes to ensure they are within 0 to 8.
          val r   = math.min(math.max(row, 0), 8)
          val c   = math.min(math.max(col, 0), 8)
          board(r)(c) = digit
        }

        // Convert the 2D board into a single string, reading row-major.
        board.map(_.mkString).mkString
      }
    }

}
