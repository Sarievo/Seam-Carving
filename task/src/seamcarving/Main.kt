package seamcarving

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

val s: Scanner = Scanner(System.`in`)
val msg: List<String> = listOf(
    "Enter rectangle width:\n", "Enter rectangle height:\n", "Enter output image name:\n" // 0, 1, 2
)

fun prompt(i: Int): String {
    print(msg[i])
    return s.nextLine().trim()
}

fun testAPI() {
    val width: Int = prompt(0).toInt()
    val height: Int = prompt(1).toInt()
    val fileName: String = prompt(2)

    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.color = Color.RED
    graphics.drawLine(0, 0, width - 1, height - 1) // 0 to 19 is 20
    graphics.drawLine(width - 1, 0, 0, height - 1)
    ImageIO.write(image, "PNG", File(fileName))
}

fun negatePhoto(inPath: String, outPath: String) {
    val image: BufferedImage = ImageIO.read(File(inPath))
    val width: Int = image.width
    val height: Int = image.height
    for (x in 0 until width) {
        for (y in 0 until height) {
            val color = Color(image.getRGB(x, y))
            image.setRGB(x, y, Color(255 - color.red, 255 - color.green, 255 - color.blue).rgb)
        }
    }
    ImageIO.write(image, "PNG", File(outPath))
}

object Energizer {
    private var width: Int = -1
    private var height: Int = -1
    private var maxEnergyValue: Double = -1.0
    lateinit var image: BufferedImage
    private var energyMatrix: Array<DoubleArray> = arrayOf()

    fun invoke(inPath: String): Array<DoubleArray> {
        mapEnergy(inPath)
        normalizeImg(false)
        return energyMatrix
    }

    fun invoke(inPath: String, outPath: String) {
        mapEnergy(inPath)
        normalizeImg(true)
        ImageIO.write(image, "PNG", File(outPath))
    }

    private fun normalizeImg(modifyImg: Boolean = false) {
        // normalize image
        for (x in 0 until width) {
            for (y in 0 until height) {
                val intensity: Int = normalise(energyMatrix[y][x])
                if (modifyImg) image.setRGB(x, y, Color(intensity, intensity, intensity).rgb)
            }
        }
    }

    private fun mapEnergy(inPath: String) {
        image = ImageIO.read(File(inPath))
        height = image.height
        width = image.width
        energyMatrix = Array(height) { DoubleArray(width) }
        // map energy
        for (x in 0 until width) {
            for (y in 0 until height) {
                val energy: Double = E(x, y)
                energyMatrix[y][x] = energy
                if (energy > maxEnergyValue) maxEnergyValue = energy
            }
        }
    }

    private fun E(x: Int, y: Int): Double = sqrt(dX(x, y) + dY(x, y))

    private fun dX(x: Int, y: Int): Double {
        val normalizedX = when (x) {
            0 -> 1
            (width - 1) -> (width - 2)
            else -> x
        }
        return diffX(normalizedX, y)
    }

    private fun diffX(x: Int, y: Int): Double {
        val c1 = Color(image.getRGB(x - 1, y))
        val c2 = Color(image.getRGB(x + 1, y))
        return colorDiff(c1, c2).toDouble()
    }

    private fun dY(x: Int, y: Int): Double {
        val normalizedY = when (y) {
            0 -> 1
            (height - 1) -> (height - 2)
            else -> y
        }
        return diffY(x, normalizedY)
    }

    private fun diffY(x: Int, y: Int): Double {
        val c1 = Color(image.getRGB(x, y - 1))
        val c2 = Color(image.getRGB(x, y + 1))
        return colorDiff(c1, c2).toDouble()
    }

    private fun colorDiff(c1: Color, c2: Color): Int {
        val diffR = diff(c1.red, c2.red)
        val diffG = diff(c1.green, c2.green)
        val diffB = diff(c1.blue, c2.blue)
        return diffR * diffR + diffG * diffG + diffB * diffB
    }

    private fun diff(c1: Int, c2: Int): Int = max(c1, c2) - min(c1, c2)

    private fun normalise(energy: Double): Int = (255.0 * energy / maxEnergyValue).toInt()
}

fun paintSeam(inPath: String, outPath: String) {
    val energyMatrix: Array<DoubleArray> = Energizer.invoke(inPath)
    val seam = carve(energyMatrix)
    println("The seam size: ${seam.size}")
    println("The seam path: ${seam.joinToString(" -> ")}")
    val image: BufferedImage = Energizer.image
    val highlight = Color(255, 0, 0)
    seam.forEach { p -> image.setRGB(p!!.first, p.second, highlight.rgb) }
    ImageIO.write(image, "PNG", File(outPath))
}

fun carve(energies: Array<DoubleArray>): Array<Pair<Int, Int>?> {
    val seamEnergies: MutableList<List<SeamEnergyWithBackPointer?>> = mutableListOf()
    // Initialize the top row of seam energies by copying over the top
    // row of the pixel energies. There are no back pointers in the top row.
    seamEnergies.add(energies[0].map { SeamEnergyWithBackPointer(it) })
    for (y in energies.indices) {
        val seamEnergiesRow: MutableList<SeamEnergyWithBackPointer?> = mutableListOf()
        energies[y].forEachIndexed { x, energy ->
            // Determine the range of x values to iterate over in the
            // previous row. The range depends on if the current pixel
            // is in the middle of the image, or on one of the edges.
            val xL = max(x - 1, 0)
            val xR = min(x + 1, energies[0].size - 1)
            val xRange: IntRange = xL..xR

            val minParentX = seamEnergies[y].withIndex().filter { it.index in xRange }
                .minByOrNull { (_, e) -> e!!.energy }!!.index
            val minSeamEnergy = SeamEnergyWithBackPointer(
                seamEnergies[y][minParentX]!!.energy + energy,
                minParentX
            )
            seamEnergiesRow.add(minSeamEnergy)
        }
        seamEnergies.add(seamEnergiesRow)
    }
    // Find the x coordinate with minimal seam energy in the bottom row.
    val minSeamEndX = seamEnergies[seamEnergies.size - 1].withIndex().minByOrNull { (_, e) -> e!!.energy }!!.index
    // Follow the back pointers to form a list of coordinates that
    // form the lowest-energy seam.
    val seam: Array<Pair<Int, Int>?> = Array(energies.size) { null }
    var seamPointX: Int = minSeamEndX
    for (y in seamEnergies.size - 1 downTo 1) {
        seam[y - 1] = Pair(seamPointX, y - 1)
        seamPointX = seamEnergies[y][seamPointX]!!.xCoordinateInPreviousRow!!
    }
    return seam
    // check reference at: https://medium.com/swlh/real-world-dynamic-programming-seam-carving-9d11c5b0bfca
}

class SeamEnergyWithBackPointer(val energy: Double, val xCoordinateInPreviousRow: Int? = null)

fun main(args: Array<String>) {
//    if (args.size == 4) Energizer.invoke(args[1], args[3])
    if (args.size == 4) paintSeam(args[1], args[3])
//    test()
}

fun test() {
    val energies: Array<IntArray> = arrayOf(
        intArrayOf(9, 9, 0, 9, 9),
        intArrayOf(9, 1, 9, 8, 9),
        intArrayOf(9, 9, 9, 9, 0),
        intArrayOf(9, 9, 9, 0, 9)
    )
    energies.forEach { println(it.contentToString()) }
}