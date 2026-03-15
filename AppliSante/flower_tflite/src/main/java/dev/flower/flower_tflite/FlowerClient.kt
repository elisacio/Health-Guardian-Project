package dev.flower.flower_tflite


import android.util.Log
import dev.flower.flower_tflite.helpers.assertIntsEqual
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.MappedByteBuffer
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.withLock
import kotlin.concurrent.write


class FlowerClient<X : Any, Y : Any>(
    tfliteFileBuffer: MappedByteBuffer,
    val layersSizes: IntArray,
    val spec: SampleSpec<X, Y>,
) : AutoCloseable {


    // XNNPACK désactivé — il ne supporte pas les shapes dynamiques.
    // Le Flex delegate (SELECT_TF_OPS) les gère sans problème.
    val interpreter = Interpreter(
        tfliteFileBuffer,
        Interpreter.Options().apply {
            setUseXNNPACK(false)
            setNumThreads(4)
        }
    )


    val interpreterLock = ReentrantLock()
    val trainingSamples = mutableListOf<Sample<X, Y>>()
    val testSamples     = mutableListOf<Sample<X, Y>>()
    val trainSampleLock = ReentrantReadWriteLock()
    val testSampleLock  = ReentrantReadWriteLock()


    private var currentWeights: Array<ByteBuffer> = emptyParameterMap().values
        .map { it as ByteBuffer }
        .toTypedArray()


    private val nClasses: Int = layersSizes.last() / 4


    fun addSample(bottleneck: X, label: Y, isTraining: Boolean) {
        val samples = if (isTraining) trainingSamples else testSamples
        val lock    = if (isTraining) trainSampleLock else testSampleLock
        lock.write { samples.add(Sample(bottleneck, label)) }
    }


    fun getParameters(): Array<ByteBuffer> {
        val inputs  = parametersToMap(currentWeights)
        val outputs = emptyParameterMap()
        runSignatureLocked(inputs, outputs, "parameters")
        val result = parametersFromMap(outputs)
        currentWeights = result.map { it.duplicate() }.toTypedArray()
        return result
    }


    fun initVariables() {
        val zeroWeights = layersSizes.map { sizeBytes ->
            ByteBuffer.allocate(sizeBytes).also { it.rewind() }
        }.toTypedArray()
        val inputs  = parametersToMap(zeroWeights)
        val outputs = emptyParameterMap()
        runSignatureLocked(inputs, outputs, "init")
        currentWeights = parametersFromMap(outputs)
        Log.d(TAG, "initVariables OK — nClasses=$nClasses")
    }


    fun updateParameters(parameters: Array<ByteBuffer>): Array<ByteBuffer> {
        val outputs = emptyParameterMap()
        runSignatureLocked(parametersToMap(parameters), outputs, "restore")
        val result = parametersFromMap(outputs)
        currentWeights = result.map { it.duplicate() }.toTypedArray()
        return result
    }


    fun fit(
        epochs: Int = 1, batchSize: Int = 32, lossCallback: ((List<Float>) -> Unit)? = null
    ): List<Double> {
        Log.d(TAG, "Starting to train for $epochs epochs, batchSize=$batchSize.")
        return trainSampleLock.write {
            (1..epochs).map { epoch ->
                val losses = trainOneEpoch(batchSize)
                Log.d(TAG, "Epoch $epoch: avg_loss=${losses.average()}")
                lossCallback?.invoke(losses)
                losses.average()
            }
        }
    }


    fun evaluate(): Pair<Float, Float> {
        val result = testSampleLock.read {
            val bottlenecks = testSamples.map { it.bottleneck }
            val logits = inference(spec.convertX(bottlenecks))
            spec.loss(testSamples, logits) to spec.accuracy(testSamples, logits)
        }
        Log.d(TAG, "Evaluate: $result")
        return result
    }


    fun inference(x: Array<X>): Array<Y> {
        val allLogits = spec.emptyY(x.size)


        for ((i, sample) in x.withIndex()) {
            val singleX = spec.convertX(listOf(sample))
            val inputs  = mutableMapOf<String, Any>("x" to singleX)
            currentWeights.forEachIndexed { j, buf ->
                buf.rewind()
                inputs["a$j"] = buf
            }
            val logitBuffer = FloatBuffer.allocate(nClasses)
            val outputs     = mapOf<String, Any>("logits" to logitBuffer)
            runSignatureLocked(inputs, outputs, "infer")


            logitBuffer.rewind()
            val logitArray = FloatArray(nClasses) { logitBuffer.get() }


            @Suppress("UNCHECKED_CAST")
            allLogits[i] = logitArray as Y
        }


        return allLogits
    }


    // ── Entraînement par vrai batch ───────────────────────────────────────────


    private fun trainOneEpoch(batchSize: Int): List<Float> {
        if (trainingSamples.isEmpty()) return listOf()


        trainingSamples.shuffle()
        val losses = mutableListOf<Float>()


        var idx = 0
        while (idx < trainingSamples.size) {
            val end   = minOf(idx + batchSize, trainingSamples.size)
            val batch = trainingSamples.subList(idx, end)
            losses.add(trainBatch(batch))
            idx = end
        }


        return losses
    }


    private fun trainBatch(batch: List<Sample<X, Y>>): Float {
        // Préparer x : Array<X> de taille batch
        val batchX = spec.convertX(batch.map { it.bottleneck })


        // Préparer y : FloatArray 1D de taille batch — argmax du one-hot
        val batchY = FloatArray(batch.size) { i ->
            val label = batch[i].label
            when (label) {
                is FloatArray -> label.indices.maxByOrNull { label[it] }!!.toFloat()
                is Float      -> label
                is Int        -> label.toFloat()
                else          -> (label as FloatArray).let { arr ->
                    arr.indices.maxByOrNull { arr[it] }!!.toFloat()
                }
            }
        }


        val inputs = mutableMapOf<String, Any>(
            "x" to batchX,
            "y" to batchY,
        )
        currentWeights.forEachIndexed { i, buf ->
            buf.rewind()
            inputs["a$i"] = buf
        }


        val loss    = FloatBuffer.allocate(1)
        val outputs = mutableMapOf<String, Any>("loss" to loss)
        layersSizes.forEachIndexed { i, size ->
            outputs["a$i"] = ByteBuffer.allocate(size)
        }


        runSignatureLocked(inputs, outputs, "train")


        // Mettre à jour les poids avec ceux retournés par "train"
        currentWeights = Array(layersSizes.size) { i ->
            val buf = outputs["a$i"] as ByteBuffer
            buf.rewind()
            buf
        }


        return loss.get(0)
    }


    fun parametersFromMap(map: Map<String, Any>): Array<ByteBuffer> {
        assertIntsEqual(layersSizes.size, map.size)
        return (0 until map.size).map {
            val buffer = map["a$it"] as ByteBuffer
            buffer.rewind()
            buffer
        }.toTypedArray()
    }


    fun parametersToMap(parameters: Array<ByteBuffer>): Map<String, Any> {
        assertIntsEqual(layersSizes.size, parameters.size)
        return parameters.mapIndexed { index, bytes ->
            bytes.rewind()
            "a$index" to bytes
        }.toMap()
    }


    private fun runSignatureLocked(
        inputs: Map<String, Any>,
        outputs: Map<String, Any>,
        signatureKey: String
    ) {
        interpreterLock.withLock {
            interpreter.runSignature(inputs, outputs, signatureKey)
        }
    }


    private fun emptyParameterMap(): Map<String, Any> {
        return layersSizes.mapIndexed { index, size ->
            "a$index" to ByteBuffer.allocate(size)
        }.toMap()
    }


    companion object {
        private const val TAG = "Flower Client"
    }


    override fun close() {
        interpreter.close()
    }
}


data class Sample<X, Y>(val bottleneck: X, val label: Y)


class FakeNonEmptyMap<K, V> : HashMap<K, V>() {
    override fun isEmpty(): Boolean = false
}
