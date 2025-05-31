
class ScanQrCodeActivity : BaseCoroutineStateActivity<Unit>(defaultState = Unit) {

    override val layoutId = R.layout.scan_qrcode_activity

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {}

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        val binding = ScanQrcodeActivityBinding.bind(contentView)
        launch {
            if (permissionsRequestSimplifySuspend(Manifest.permission.CAMERA)) {
                startScanAnimation(binding)
                setupCamera(binding)
            } else {
                AndroidLog.e(TAG, "Camera permission denied.")
                finish()
            }
        }
    }

    private fun startScanAnimation(binding: ScanQrcodeActivityBinding) {
        binding.scanLineView.post {
            val animation = TranslateAnimation(0f, 0f, 0f, binding.scanLineView.measuredWidth.toFloat()).apply {
                duration = 1000
                repeatCount = Animation.INFINITE
                repeatMode = Animation.REVERSE
            }
            binding.scanLineView.startAnimation(animation)
        }
    }

    private suspend fun setupCamera(binding: ScanQrcodeActivityBinding) {
        val cameraProvider = withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(this@ScanQrCodeActivity).get()
        }
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(binding.previewView.surfaceProvider)
        }

        val analysis = createAnalyzer()

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this@ScanQrCodeActivity, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        } catch (e: Exception) {
            AndroidLog.e(TAG, "Camera start error: ${e.message}", e)
        }
    }

    private fun createAnalyzer(): ImageAnalysis {
        val options = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        val scanner = BarcodeScanning.getClient(options)
        val hasScanned = AtomicBoolean(false)

        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setBackgroundExecutor(Dispatchers.IO.asExecutor())
            .build().apply {
                setAnalyzer(Dispatchers.IO.asExecutor()) { imageProxy ->
                    val bitmap = imageProxy.toBitmap()
                    val inputImage = InputImage.fromBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
                    if (!hasScanned.get()) {
                        scanner.process(inputImage)
                            .addOnSuccessListener { results ->
                                results?.firstOrNull()?.rawValue?.let {
                                    if (hasScanned.compareAndSet(false, true)) {
                                        handleSuccess(results)
                                    }
                                }
                                
                            }
                            .addOnFailureListener {
                                AndroidLog.e(TAG, "QR scan error: ${it.message}")
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }
            }
    }

    private fun handleSuccess(barcodes: List<Barcode>) {
        runOnUiThread {
            vibrate()
            val result = Intent().apply {
                putExtra(QR_CODE_RESULT_KEY, barcodes.map { it.rawValue }.toTypedArray())
            }
            setResult(Activity.RESULT_OK, result)
            finishWithNoAnimation()
        }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            getSystemService<Vibrator>()
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun finishWithNoAnimation() {
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0, 0)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            overridePendingT
