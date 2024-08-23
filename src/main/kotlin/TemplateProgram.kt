import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.ffmpeg.VideoPlayerFFMPEG
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.renderTarget
import org.openrndr.draw.*
import org.openrndr.extra.noclear.NoClear
import org.openrndr.color.rgb

import org.openrndr.boofcv.binding.toGrayF32
import org.openrndr.boofcv.binding.toShapeContours
import boofcv.alg.filter.binary.BinaryImageOps
import boofcv.alg.filter.binary.GThresholdImageOps
import boofcv.alg.filter.binary.ThresholdImageOps
import boofcv.struct.ConnectRule
import boofcv.struct.image.GrayU8

import org.openrndr.math.CatmullRomChain2

import org.openrndr.shape.ShapeContour
import org.openrndr.shape.simplify
import org.openrndr.shape.toContour

fun main() = application {
    configure {
        width = 768
        height = 576
    }

    program {
        val videoPlayer = VideoPlayerFFMPEG.fromDevice()
        videoPlayer.play()

        val renderTarget1 = renderTarget(768, 576) {
            colorBuffer()
        }


        val renderTarget2 = renderTarget(768, 576) {
            colorBuffer()
            depthBuffer()
        }

        extend(NoClear()) {
            backdrop = { drawer.clear(rgb(0.0)) }
        }

        extend {
            drawer.isolatedWithTarget(renderTarget1) {
                scale(-1.0, 1.0)
                translate(-width * 1.0, 1.0)
                videoPlayer.draw(drawer)

            }

            val vectorized = imageToContours(renderTarget1.colorBuffer(0))

            // Make a simplified list of points
            val simplePoints = vectorized.map {
                simplify(it.adaptivePositions(), 4.0)
            }.filter { it.size >= 3 }

            // Use the simplified list to make a smooth contour
            val smooth = simplePoints.map {
                CatmullRomChain2(it, 0.0, true).toContour()
            }

            // Use the simplified list to make a polygonal contour
            val polygonal = simplePoints.map {
                ShapeContour.fromPoints(it, true)
            }

            drawer.isolatedWithTarget(renderTarget2) {
                clear(ColorRGBa.TRANSPARENT)
                translate(0.0, 1.0)
                fill = null
                stroke = ColorRGBa.BLACK.opacify(0.7)
                contours(polygonal)
                contours(smooth)
            }

            drawer.image(renderTarget1.colorBuffer(0))
            drawer.image(renderTarget2.colorBuffer(0))
        }
    }
}

fun imageToContours(input: ColorBuffer): List<ShapeContour> {
    val bitmap = input.toGrayF32()
    // BoofCV: calculate a good threshold for the loaded image
    val threshold = GThresholdImageOps.computeOtsu(bitmap, 0.0, 255.0)

    // BoofCV: use the threshold to convert the image to black and white
    val binary = GrayU8(bitmap.width, bitmap.height)
    ThresholdImageOps.threshold(bitmap, binary, threshold.toFloat(), false)

    // BoofCV: Contract and expand the white areas to remove noise
    var filtered = BinaryImageOps.erode8(binary, 1, null)
    filtered = BinaryImageOps.dilate8(filtered, 1, null)

    // BoofCV: Calculate contours as vector data
    val contours = BinaryImageOps.contour(filtered, ConnectRule.EIGHT, null)

    // orx-boofcv: convert vector data to OPENRNDR ShapeContours
    return contours.toShapeContours(true, internal = true, external = true)
}
