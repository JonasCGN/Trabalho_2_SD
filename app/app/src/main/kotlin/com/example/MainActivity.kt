package com.example

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var editTextIP: EditText
    private lateinit var editTextPort: EditText
    private lateinit var buttonTakePhoto: Button
    private lateinit var buttonSendPhoto: Button
    private lateinit var buttonViewGallery: Button
    private lateinit var imageViewPreview: ImageView
    private lateinit var textViewStatus: TextView
    
    private val CAMERA_PERMISSION_CODE = 101
    
    private var currentPhotoBitmap: Bitmap? = null
    private var currentPhotoUri: Uri? = null
    private var currentPhotoPath: String = ""
    
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        initializeActivityLaunchers()
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }
        
        val labelIP = TextView(this).apply {
            text = "IP do Servidor:"
            textSize = 16f
        }
        
        editTextIP = EditText(this).apply {
            hint = "Ex: 192.168.1.100"
            setText("192.168.1.100")
        }
        
        val labelPort = TextView(this).apply {
            text = "Porta do Servidor:"
            textSize = 16f
        }
        
        editTextPort = EditText(this).apply {
            hint = "Ex: 8080"
            setText("8080")
        }
        
        val labelPreview = TextView(this).apply {
            text = "Preview da Foto:"
            textSize = 16f
        }
        
        imageViewPreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFFE0E0E0.toInt())
            adjustViewBounds = true
            maxHeight = 400
        }
        
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        
        buttonTakePhoto = Button(this).apply {
            text = "Tirar Foto"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                checkCameraPermissionAndTakePhoto()
            }
        }
        
        buttonViewGallery = Button(this).apply {
            text = "Ver na Galeria"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isEnabled = false
            setOnClickListener {
                currentPhotoUri?.let { uri ->
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "image/*")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@MainActivity, "Nenhum app para visualizar imagens", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        buttonLayout.addView(buttonTakePhoto)
        buttonLayout.addView(buttonViewGallery)
        
        buttonSendPhoto = Button(this).apply {
            text = "Enviar Foto"
            textSize = 18f
            isEnabled = false
            setOnClickListener {
                currentPhotoBitmap?.let { bitmap ->
                    sendImageToServer(bitmap)
                } ?: run {
                    Toast.makeText(this@MainActivity, "Nenhuma foto para enviar", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        textViewStatus = TextView(this).apply {
            text = "Pronto para tirar foto"
            textSize = 14f
        }
        
        layout.addView(labelIP)
        layout.addView(editTextIP)
        layout.addView(labelPort)
        layout.addView(editTextPort)
        layout.addView(labelPreview)
        layout.addView(imageViewPreview)
        layout.addView(buttonLayout)
        layout.addView(buttonSendPhoto)
        layout.addView(textViewStatus)
        
        setContentView(layout)
    }
    
    /**
     * Inicializa os ActivityResultLaunchers modernos para câmera e permissões
     */
    private fun initializeActivityLaunchers() {
        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            try {
                if (success && currentPhotoPath.isNotEmpty()) {
                    val bitmap = processAndSaveImage(currentPhotoPath)
                    if (bitmap != null) {
                        currentPhotoBitmap = bitmap
                        
                        imageViewPreview.scaleType = ImageView.ScaleType.FIT_CENTER
                        imageViewPreview.adjustViewBounds = true
                        imageViewPreview.setImageBitmap(bitmap)
                        
                        saveImageToGalleryModern(bitmap)
                        
                        buttonSendPhoto.isEnabled = true
                        buttonViewGallery.isEnabled = true
                        
                        textViewStatus.text = "Foto capturada! Pronta para enviar.\nResolução: ${bitmap.width}x${bitmap.height}px"
                    } else {
                        Toast.makeText(this, "Erro ao processar foto", Toast.LENGTH_SHORT).show()
                        textViewStatus.text = "Erro ao processar foto. Tente novamente."
                    }
                } else {
                    Toast.makeText(this, "Foto não foi capturada", Toast.LENGTH_SHORT).show()
                    textViewStatus.text = "Foto não foi capturada. Tente novamente."
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                textViewStatus.text = "Erro ao processar foto: ${e.message}"
            }
        }
        
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                takePhoto()
            } else {
                Toast.makeText(this, "Permissão de câmera negada", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Verifica permissão de câmera e inicia captura de foto
     */
    private fun checkCameraPermissionAndTakePhoto() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                takePhoto()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    /**
     * Cria arquivo temporário para armazenar a foto capturada
     */
    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }
    
    /**
     * Inicia a captura de foto usando FileProvider
     */
    private fun takePhoto() {
        try {
            val photoFile = createImageFile()
            val photoURI: Uri = FileProvider.getUriForFile(
                this,
                "com.example.fileprovider",
                photoFile
            )
            currentPhotoUri = photoURI
            
            textViewStatus.text = "Abrindo câmera..."
            
            takePictureLauncher.launch(photoURI)
        } catch (ex: Exception) {
            Toast.makeText(this, "Erro ao criar arquivo de imagem: ${ex.message}", Toast.LENGTH_LONG).show()
            textViewStatus.text = "Erro ao abrir câmera: ${ex.message}"
        }
    }
    
    /**
     * Processa imagem: corrige rotação, redimensiona e salva versão final
     * Solução para problemas de orientação em diferentes dispositivos
     */
    private fun processAndSaveImage(imagePath: String): Bitmap? {
        return try {
            val file = java.io.File(imagePath)
            if (!file.exists()) {
                Toast.makeText(this, "Arquivo de imagem não encontrado", Toast.LENGTH_SHORT).show()
                return null
            }
            
            // 1. Carregar bitmap original
            val originalBitmap = BitmapFactory.decodeFile(imagePath)
            if (originalBitmap == null) {
                Toast.makeText(this, "Erro ao carregar imagem", Toast.LENGTH_SHORT).show()
                return null
            }
            
            // 2. Corrigir orientação usando EXIF
            val correctedBitmap = correctImageOrientation(originalBitmap, imagePath)
            
            // 3. Redimensionar se necessário
            val finalBitmap = resizeBitmapIfNeeded(correctedBitmap, 1280)
            
            // 4. Salvar versão processada e recarregar (garante consistência)
            val processedFile = saveProcessedImageToFile(finalBitmap)
            
            // 5. Recarregar da versão salva para garantir consistência
            val savedBitmap = BitmapFactory.decodeFile(processedFile.absolutePath)
            
            // Liberar bitmaps intermediários da memória
            if (originalBitmap != correctedBitmap) originalBitmap.recycle()
            if (correctedBitmap != finalBitmap) correctedBitmap.recycle()
            finalBitmap.recycle()
            
            savedBitmap
            
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao processar imagem: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }
    
    /**
     * Corrige orientação da imagem usando dados EXIF
     */
    private fun correctImageOrientation(bitmap: Bitmap, imagePath: String): Bitmap {
        return try {
            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipImage(bitmap, horizontal = true)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipImage(bitmap, horizontal = false)
                else -> bitmap
            }
        } catch (e: Exception) {
            // Se falhar ao ler EXIF, retorna bitmap original
            bitmap
        }
    }
    
    /**
     * Redimensiona bitmap se exceder o tamanho máximo
     */
    private fun resizeBitmapIfNeeded(bitmap: Bitmap, maxSize: Int): Bitmap {
        if (bitmap.width <= maxSize && bitmap.height <= maxSize) {
            return bitmap
        }
        
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val (newWidth, newHeight) = if (bitmap.width > bitmap.height) {
            maxSize to (maxSize / aspectRatio).toInt()
        } else {
            (maxSize * aspectRatio).toInt() to maxSize
        }
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Espelha bitmap horizontal ou verticalmente
     */
    private fun flipImage(bitmap: Bitmap, horizontal: Boolean): Bitmap {
        val matrix = Matrix()
        if (horizontal) {
            matrix.preScale(-1f, 1f)
        } else {
            matrix.preScale(1f, -1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    /**
     * Salva bitmap processado em arquivo temporário
     */
    private fun saveProcessedImageToFile(bitmap: Bitmap): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val processedFile = File(storageDir, "processed_${timeStamp}.jpg")
        
        FileOutputStream(processedFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        
        return processedFile
    }
    
    /**
     * Rotaciona bitmap em graus especificados
     */
    private fun rotateImage(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    /**
     * Salva imagem na galeria usando MediaStore API moderna
     */
    private fun saveImageToGalleryModern(bitmap: Bitmap) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "CameraApp_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }

            val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            
            imageUri?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                }
                currentPhotoUri = uri
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao salvar na galeria: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Envia imagem para servidor via socket TCP
     * Protocolo: 4 bytes (tamanho) + dados JPEG
     */
    private fun sendImageToServer(bitmap: Bitmap) {
        val ip = editTextIP.text.toString().trim()
        val portText = editTextPort.text.toString().trim()
        
        if (ip.isEmpty() || portText.isEmpty()) {
            Toast.makeText(this, "IP e Porta são obrigatórios", Toast.LENGTH_SHORT).show()
            return
        }
        
        val port = portText.toIntOrNull()
        if (port == null || port <= 0) {
            Toast.makeText(this, "Porta inválida", Toast.LENGTH_SHORT).show()
            return
        }
        
        textViewStatus.text = "Enviando foto...\n"
        buttonSendPhoto.isEnabled = false
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket()
                
                socket.connect(java.net.InetSocketAddress(ip, port), 5000)
                socket.soTimeout = 5000
                
                val outputStream = socket.getOutputStream()
                
                val byteArrayOutputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
                val imageBytes = byteArrayOutputStream.toByteArray()
                
                val dataOutputStream = DataOutputStream(outputStream)
                dataOutputStream.writeInt(imageBytes.size)
                
                outputStream.write(imageBytes)
                outputStream.flush()
                
                socket.close()
                
                withContext(Dispatchers.Main) {
                    textViewStatus.text = "Foto enviada com sucesso!\nTamanho: ${imageBytes.size / 1024}KB\nTimeout: 5s\n"
                    buttonSendPhoto.isEnabled = true
                    Toast.makeText(this@MainActivity, "Foto enviada!", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: java.net.SocketTimeoutException) {
                withContext(Dispatchers.Main) {
                    textViewStatus.text = "Timeout: Servidor não respondeu em 5 segundos\nVerifique se o servidor está rodando\n"
                    buttonSendPhoto.isEnabled = true
                    Toast.makeText(this@MainActivity, "Timeout: Servidor não respondeu", Toast.LENGTH_LONG).show()
                }
            } catch (e: java.net.ConnectException) {
                withContext(Dispatchers.Main) {
                    textViewStatus.text = "Erro de conexão: Servidor indisponível\nVerifique IP/Porta e se o servidor está rodando\n"
                    buttonSendPhoto.isEnabled = true
                    Toast.makeText(this@MainActivity, "Erro: Servidor indisponível", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    textViewStatus.text = "Erro ao enviar: ${e.message}\nTimeout: 5s\n"
                    buttonSendPhoto.isEnabled = true
                    Toast.makeText(this@MainActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}