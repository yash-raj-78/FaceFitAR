package com.yash.facefitar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnCapture: Button
    private lateinit var glassesOverlay: ImageView

    private lateinit var btnGlasses: Button
    private lateinit var btnEars: Button
    private lateinit var btnCrown: Button

    private var currentFilter = "glasses"

    private var imageCapture: ImageCapture? = null
    private val CAMERA_PERMISSION_CODE = 100

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        FaceDetection.getClient(options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        previewView = findViewById(R.id.previewView)
        btnCapture = findViewById(R.id.btnCapture)
        glassesOverlay = findViewById(R.id.glassesOverlay)

        btnGlasses = findViewById(R.id.btnGlasses)
        btnEars = findViewById(R.id.btnEars)
        btnCrown = findViewById(R.id.btnCrown)

        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        // ✅ Filter buttons
        btnGlasses.setOnClickListener {
            currentFilter = "glasses"
            glassesOverlay.setImageResource(R.drawable.glasses)
        }

        btnEars.setOnClickListener {
            currentFilter = "ears"
            glassesOverlay.setImageResource(R.drawable.ears)
        }

        btnCrown.setOnClickListener {
            currentFilter = "crown"
            glassesOverlay.setImageResource(R.drawable.crown)
        }

        // Permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            startCamera()
        }

        btnCapture.setOnClickListener { takePhoto() }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder().build()

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                    processFaceDetection(imageProxy)
                }

                val cameraSelector = when {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                        CameraSelector.DEFAULT_BACK_CAMERA
                    else -> {
                        Toast.makeText(this, "No camera found", Toast.LENGTH_LONG).show()
                        return@addListener
                    }
                }

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )

            } catch (e: Exception) {
                Toast.makeText(this, "Camera failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processFaceDetection(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image

        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            detector.process(image)
                .addOnSuccessListener { faces ->
                    runOnUiThread {
                        if (faces.isNotEmpty()) {
                            val face = faces[0]
                            val bounds = face.boundingBox

                            val layoutParams = glassesOverlay.layoutParams

                            when (currentFilter) {

                                "glasses" -> {
                                    layoutParams.width = bounds.width()
                                    layoutParams.height = bounds.height() / 3

                                    glassesOverlay.x = bounds.left.toFloat()
                                    glassesOverlay.y =
                                        bounds.top.toFloat() + (bounds.height() / 4)
                                }

                                "ears" -> {
                                    layoutParams.width = bounds.width()
                                    layoutParams.height = bounds.height() / 2

                                    glassesOverlay.x = bounds.left.toFloat()
                                    glassesOverlay.y =
                                        bounds.top.toFloat() - (bounds.height() / 2)
                                }

                                "crown" -> {
                                    layoutParams.width = bounds.width()
                                    layoutParams.height = bounds.height() / 2

                                    glassesOverlay.x = bounds.left.toFloat()
                                    glassesOverlay.y =
                                        bounds.top.toFloat() - (bounds.height() / 2.5f)
                                }
                            }

                            glassesOverlay.layoutParams = layoutParams
                            glassesOverlay.visibility = View.VISIBLE

                        } else {
                            glassesOverlay.visibility = View.GONE
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }

        } else {
            imageProxy.close()
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val mediaDir = externalMediaDirs.firstOrNull() ?: filesDir

        val photoFile = File(
            mediaDir,
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Toast.makeText(this@CameraActivity, "Photo Saved", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        this@CameraActivity,
                        "Capture Failed: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == CAMERA_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_SHORT).show()
        }
    }
}