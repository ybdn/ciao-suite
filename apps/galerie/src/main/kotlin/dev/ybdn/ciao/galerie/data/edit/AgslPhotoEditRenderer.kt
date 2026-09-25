package dev.ybdn.ciao.galerie.data.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RenderNode
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.graphics.Gainmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import dev.ybdn.ciao.galerie.domain.model.EditRecipe
import dev.ybdn.ciao.galerie.domain.repository.EncodedFormat
import dev.ybdn.ciao.galerie.domain.repository.PhotoEditRenderer
import dev.ybdn.ciao.galerie.domain.repository.RenderedImage
import dev.ybdn.ciao.galerie.domain.util.RecipeGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
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

    private val shader by lazy { PhotoAdjustmentShader(context) }

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
            val geometryOutput = transformed(source, recipe, outputWidth, outputHeight, scale = 1.0)
            val output = if (recipe.hasPixelAdjustments) {
                try {
                    shaded(geometryOutput, recipe)
                } finally {
                    geometryOutput.recycle()
                }
            } else {
                geometryOutput
            }
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

    /**
     * Applique le shader des réglages par tuiles de [TILE_SIZE] px (recouvrement de [TILE_OVERLAP] px
     * pour la netteté), sous la taille maximale de texture. Les pixels sont traités tels quels :
     * l'espace colorimétrique (ex. Display P3) est retiré le temps du rendu puis réattribué, sans
     * conversion ni perte de gamut.
     */
    private fun shaded(input: Bitmap, recipe: EditRecipe): Bitmap {
        val colorSpace = input.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB)
        // Copie 8 bits modifiable (une source 10 bits, RGBA_F16, y est convertie).
        val working = input.copy(Bitmap.Config.ARGB_8888, true)
        working.setColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
        val width = working.width
        val height = working.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888, working.hasAlpha(), ColorSpace.get(ColorSpace.Named.SRGB))
        val canvas = Canvas(output)
        try {
            var top = 0
            while (top < height) {
                var left = 0
                val tileHeight = min(TILE_SIZE, height - top)
                while (left < width) {
                    val tileWidth = min(TILE_SIZE, width - left)
                    val renderLeft = max(0, left - TILE_OVERLAP)
                    val renderTop = max(0, top - TILE_OVERLAP)
                    val renderRight = min(width, left + tileWidth + TILE_OVERLAP)
                    val renderBottom = min(height, top + tileHeight + TILE_OVERLAP)
                    val tile = renderTile(working, recipe, renderLeft, renderTop, renderRight - renderLeft, renderBottom - renderTop)
                    try {
                        val sourceRect = Rect(left - renderLeft, top - renderTop, left - renderLeft + tileWidth, top - renderTop + tileHeight)
                        canvas.drawBitmap(tile, sourceRect, Rect(left, top, left + tileWidth, top + tileHeight), null)
                    } finally {
                        tile.recycle()
                    }
                    left += tileWidth
                }
                top += tileHeight
            }
        } finally {
            working.recycle()
        }
        output.setColorSpace(colorSpace.takeIf { it.model == ColorSpace.Model.RGB && !it.isWideGamutF16() } ?: ColorSpace.get(ColorSpace.Named.SRGB))
        return output
    }

    private fun ColorSpace.isWideGamutF16(): Boolean =
        this == ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB) || this == ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB)

    /** Rendu GPU d'une zone de [input] : `RenderNode` dessiné par `HardwareRenderer` dans un `ImageReader`. */
    private fun renderTile(input: Bitmap, recipe: EditRecipe, left: Int, top: Int, width: Int, height: Int): Bitmap {
        val reader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val renderer = HardwareRenderer()
        try {
            val bitmapShader = BitmapShader(input, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setTranslate(-left.toFloat(), -top.toFloat()) })
            }
            val runtimeShader = shader.create(
                recipe.effectiveAdjustments,
                recipe.effectiveMono,
                frameLeft = -left.toFloat(),
                frameTop = -top.toFloat(),
                frameWidth = input.width.toFloat(),
                frameHeight = input.height.toFloat(),
            ).apply { setInputShader("image", bitmapShader) }
            val node = RenderNode("tile").apply { setPosition(0, 0, width, height) }
            node.beginRecording().drawPaint(Paint().apply { this.shader = runtimeShader; blendMode = BlendMode.SRC })
            node.endRecording()
            renderer.setContentRoot(node)
            renderer.setSurface(reader.surface)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            val image = reader.acquireNextImage() ?: throw IOException("Rendu GPU indisponible")
            image.use {
                val buffer = it.hardwareBuffer ?: throw IOException("Rendu GPU indisponible")
                buffer.use { hardwareBuffer ->
                    val wrapped = Bitmap.wrapHardwareBuffer(hardwareBuffer, ColorSpace.get(ColorSpace.Named.SRGB))
                        ?: throw IOException("Rendu GPU illisible")
                    return wrapped.copy(Bitmap.Config.ARGB_8888, false).also { wrapped.recycle() }
                }
            }
        } finally {
            renderer.destroy()
            reader.close()
        }
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
        const val TILE_SIZE = 4096
        const val TILE_OVERLAP = 16
    }
}
