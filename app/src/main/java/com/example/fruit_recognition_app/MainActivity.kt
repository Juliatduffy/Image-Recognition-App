package com.example.fruit_recognition_app

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import android.Manifest


class MainActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private lateinit var resultText: TextView
    private lateinit var cameraButton: Button
    private lateinit var galleryButton: Button

    private val takePicturePreview = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            imageView.setImageBitmap(it)
            runModel(it)
        }
    }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream = contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            imageView.setImageBitmap(bitmap)
            runModel(bitmap)
        }
    }

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            takePicturePreview.launch(null)
        } else {
            Toast.makeText(this, "Permission Denied!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        resultText = findViewById(R.id.resultText)
        cameraButton = findViewById(R.id.cameraButton)
        galleryButton = findViewById(R.id.galleryButton)

        cameraButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                takePicturePreview.launch(null)
            } else {
                requestPermission.launch(Manifest.permission.CAMERA)
            }
        }

        galleryButton.setOnClickListener {
            pickImage.launch("image/*")
        }
    }

    private fun runModel(bitmap: Bitmap) {
        val input = bitmapToModelInput(bitmap)
        val output = Array(1) { FloatArray(5) }

        val model = Interpreter(loadModelFile("model.tflite"))
        model.run(input, output)
        model.close()

        val labels = assets.open("labels.txt").bufferedReader().readLines()
        val predictedIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
        val prediction = labels.getOrNull(predictedIndex) ?: "Unknown"

        resultText.text = "Prediction: $prediction"
    }

    private fun loadModelFile(modelName: String): ByteBuffer {
        val fileDescriptor = assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun bitmapToModelInput(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val resized = Bitmap.createScaledBitmap(bitmap, 100, 100, true)
        val input = Array(1) { Array(100) { Array(100) { FloatArray(3) } } }

        for (y in 0 until 100) {
            for (x in 0 until 100) {
                val pixel = resized.getPixel(x, y)
                input[0][y][x][0] = Color.red(pixel).toFloat()  // no division
                input[0][y][x][1] = Color.green(pixel).toFloat()
                input[0][y][x][2] = Color.blue(pixel).toFloat()
            }
        }

        return input
    }
}