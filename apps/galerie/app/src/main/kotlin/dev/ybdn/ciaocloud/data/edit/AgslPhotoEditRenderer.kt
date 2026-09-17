package dev.ybdn.ciaocloud.data.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Gainmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import dev.ybdn.ciaocloud.domain.model.EditRecipe
import dev.ybdn.ciaocloud.domain.repository.EncodedFormat
import dev.ybdn.ciaocloud.domain.repository.PhotoEditRenderer
import dev.ybdn.ciaocloud.domain.repository.RenderedImage
import dev.ybdn.ciaocloud.domain.util.RecipeGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Rendu pleine résolution : décodage complet (orientation EXIF appliquée), géométrie sur CPU
 * (`Canvas` + `Matrix`, filtrage bilinéaire), encodage. Le bitmap produit conserve l'espace
 * colorimétrique de la source, dont le profil ICC est intégré à l'encodage. La carte de gain Ultra HDR
 * (API 34+) reçoit la même transformation, à son échelle, et reste attachée au résultat.
 */
class AgslPhotoEditRenderer(
    private val context: Context,
) : PhotoEditRenderer {

    override suspend fun render(
        sourceUri: String,
        recipe: EditRecipe,
        format: EncodedFormat,
        outputPath: String,
    ): RenderedImage = withContext(Dispatchers.Default) {
        val source = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, Uri.parse(sourceUri))) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            ensureActive()
            val geometry = RecipeGeometry(source.width.toDouble(), source.height.toDouble())
            val (outputWidth, outputHeight) = geometry.outputSize(recipe)
            val output = transformed(source, recipe, outputWidth, outputHeight, scale = 1.0)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) attachGainMap(source, output, recipe)
                ensureActive()
                withContext(Dispatchers.IO) { encode(output, format, File(outputPath)) }
                RenderedImage(output.width, output.height)
            } finally {
                output.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    /**
     * Applique la géométrie de la recette à [bitmap]. [scale] : rapport entre [bitmap] et l'image
     * pleine résolution (carte de gain plus petite que l'image).
     */
    private fun transformed(bitmap: Bitmap, recipe: EditRecipe, outputWidth: Int, outputHeight: Int, scale: Double): Bitmap {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val transform = recipe.transform
        val orientedWidth = if (transform.swapsDimensions) height else width
        val orientedHeight = if (transform.swapsDimensions) width else height
        val matrix = Matrix().apply {
            if (transform.flipped) {
                postScale(-1f, 1f)
                postTranslate(width, 0f)
            }
            when (transform.rotationDegrees) {
                90 -> {
                    postRotate(90f)
                    postTranslate(height, 0f)
                }
                180 -> {
                    postRotate(180f)
                    postTranslate(width, height)
                }
                270 -> {
                    postRotate(270f)
                    postTranslate(0f, width)
                }
            }
            if (recipe.straightenDegrees != 0.0) {
                postRotate(recipe.straightenDegrees.toFloat(), orientedWidth / 2, orientedHeight / 2)
            }
            postTranslate(-(recipe.crop.left * orientedWidth).toFloat(), -(recipe.crop.top * orientedHeight).toFloat())
        }
        val targetWidth = (outputWidth * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (outputHeight * scale).roundToInt().coerceAtLeast(1)
        val config = bitmap.config ?: Bitmap.Config.ARGB_8888
        val colorSpace = bitmap.colorSpace
        // Carte de gain monocanal (ALPHA_8) : pas d'espace colorimétrique.
        val output = if (config == Bitmap.Config.ALPHA_8 || colorSpace == null) {
            Bitmap.createBitmap(targetWidth, targetHeight, config)
        } else {
            Bitmap.createBitmap(targetWidth, targetHeight, config, bitmap.hasAlpha(), colorSpace)
        }
        Canvas(output).drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return output
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun attachGainMap(source: Bitmap, output: Bitmap, recipe: EditRecipe) {
        val gainMap = source.gainmap ?: return
        val contents = gainMap.gainmapContents
        val scale = contents.width.toDouble() / source.width
        val transformedContents = transformed(contents, recipe, output.width, output.height, scale)
        output.gainmap = Gainmap(transformedContents).apply {
            gainMap.ratioMin.let { setRatioMin(it[0], it[1], it[2]) }
            gainMap.ratioMax.let { setRatioMax(it[0], it[1], it[2]) }
            gainMap.gamma.let { setGamma(it[0], it[1], it[2]) }
            gainMap.epsilonSdr.let { setEpsilonSdr(it[0], it[1], it[2]) }
            gainMap.epsilonHdr.let { setEpsilonHdr(it[0], it[1], it[2]) }
            displayRatioForFullHdr = gainMap.displayRatioForFullHdr
            minDisplayRatioForHdrTransition = gainMap.minDisplayRatioForHdrTransition
        }
    }

    private fun encode(bitmap: Bitmap, format: EncodedFormat, file: File) {
        val (compressFormat, quality) = when (format) {
            EncodedFormat.JPEG -> Bitmap.CompressFormat.JPEG to QUALITY
            EncodedFormat.PNG -> Bitmap.CompressFormat.PNG to 100
            EncodedFormat.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY to QUALITY
        }
        file.outputStream().use { output ->
            if (!bitmap.compress(compressFormat, quality, output)) throw IOException("Encodage impossible")
            output.fd.sync()
        }
    }

    private companion object {
        const val QUALITY = 95
    }
}
