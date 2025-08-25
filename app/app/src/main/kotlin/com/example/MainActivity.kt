package com.example

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
    
    // ActivityResultLauncher moderno para câmera
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    
    // ActivityResultLauncher para permissões
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar ActivityResultLaunchers
        initializeActivityLaunchers()
        
        // Criar layout programaticamente
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }
        
        // Campo IP
        val labelIP = TextView(this).apply {
            text = "IP do Servidor:"
            textSize = 16f
        }
        
        editTextIP = EditText(this).apply {
            hint = "Ex: 192.168.1.100"
            setText("192.168.1.100") // IP padrão
        }
        
        // Campo Porta
        val labelPort = TextView(this).apply {
            text = "Porta do Servidor:"
            textSize = 16f
        }
        
        editTextPort = EditText(this).apply {
            hint = "Ex: 8080"
            setText("8080") // Porta padrão
        }
        
        // Preview da imagem
        val labelPreview = TextView(this).apply {
            text = "Preview da Foto:"
            textSize = 16f
        }
        
        imageViewPreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(400, 300)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFFE0E0E0.toInt())
        }
        
        // Botões em linha horizontal
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        
        // Botão Tirar Foto
        buttonTakePhoto = Button(this).apply {
            text = "Tirar Foto"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                checkCameraPermissionAndTakePhoto()
            }
        }
        
        // Botão Ver na Galeria
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
        
        // Botão Enviar Foto
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
        
        // Status
        // textViewStatus = TextView(this).apply {
        //     text = "Pronto para tirar foto\n\nUsando: Intent nativa da câmera + Kotlin Coroutines\nFormato: JPEG 80% qualidade, máx. 1280px\nTimeout: 5 segundos\nAPIs modernas: ActivityResultLauncher + MediaStore"
        //     textSize = 14f
        // }
        
        // Adicionar views ao layout
        layout.addView(labelIP)
        layout.addView(editTextIP)
        layout.addView(labelPort)
        layout.addView(editTextPort)
        layout.addView(labelPreview)
        layout.addView(imageViewPreview)
        layout.addView(buttonLayout)
        layout.addView(buttonSendPhoto)
        // layout.addView(textViewStatus)
        
        setContentView(layout)
    }
    
    private fun initializeActivityLaunchers() {
        // Launcher moderno para tirar foto
        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && currentPhotoPath.isNotEmpty()) {
                val bitmap = resizeAndCompressBitmap(currentPhotoPath)
                if (bitmap != null) {
                    currentPhotoBitmap = bitmap
                    imageViewPreview.setImageBitmap(bitmap)
                    
                    // Salvar versão otimizada na galeria usando API moderna
                    saveImageToGalleryModern(bitmap)
                    
                    // Habilitar botões
                    buttonSendPhoto.isEnabled = true
                    buttonViewGallery.isEnabled = true
                    
                    textViewStatus.text = "Foto capturada! Pronta para enviar.\nResolução: ${bitmap.width}x${bitmap.height}px\nTimeout: 5 segundos\nFormato: JPEG 80% qualidade, máx. 1280px\nAPIs modernas: ActivityResultLauncher + MediaStore"
                } else {
                    Toast.makeText(this, "Erro ao processar foto", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Launcher moderno para permissões
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                takePhoto()
            } else {
                Toast.makeText(this, "Permissão de câmera negada", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun checkCameraPermissionAndTakePhoto() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                takePhoto()
            }
            else -> {
                // Usar launcher moderno para pedir permissão
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Criar nome único para o arquivo
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
    
    private fun takePhoto() {
        try {
            val photoFile = createImageFile()
            val photoURI: Uri = FileProvider.getUriForFile(
                this,
                "com.example.fileprovider",
                photoFile
            )
            currentPhotoUri = photoURI
            
            // Usar launcher moderno em vez de startActivityForResult
            takePictureLauncher.launch(photoURI)
        } catch (ex: IOException) {
            Toast.makeText(this, "Erro ao criar arquivo de imagem", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun resizeAndCompressBitmap(imagePath: String): Bitmap? {
        // Primeiro, obter dimensões da imagem
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imagePath, options)
        
        // Calcular fator de escala para máximo de 1280px
        val maxSize = 1280
        var scaleFactor = 1
        if (options.outHeight > maxSize || options.outWidth > maxSize) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            
            while (halfHeight / scaleFactor >= maxSize && halfWidth / scaleFactor >= maxSize) {
                scaleFactor *= 2
            }
        }
        
        // Decodificar com fator de escala
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = scaleFactor
        }
        
        val bitmap = BitmapFactory.decodeFile(imagePath, decodeOptions)
        
        // Se ainda for maior que 1280px, redimensionar mais
        return if (bitmap != null && (bitmap.width > maxSize || bitmap.height > maxSize)) {
            val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val (newWidth, newHeight) = if (bitmap.width > bitmap.height) {
                maxSize to (maxSize / aspectRatio).toInt()
            } else {
                (maxSize * aspectRatio).toInt() to maxSize
            }
            
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
    }
    
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
    
    // Remover métodos deprecated
    // onRequestPermissionsResult e onActivityResult não são mais necessários
    
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
        
        textViewStatus.text = "Enviando foto...\nTimeout: 5 segundos\nFormato: JPEG 80% qualidade, máx. 1280px\nAPIs modernas: ActivityResultLauncher + MediaStore"
        buttonSendPhoto.isEnabled = false
        
        // Usando Kotlin Coroutines para operação de rede com timeout
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket()
                
                // Configurar timeout de 5 segundos para conexão
                socket.connect(java.net.InetSocketAddress(ip, port), 5000)
                socket.soTimeout = 5000 // Timeout para operações de leitura/escrita
                
                val outputStream = socket.getOutputStream()
                
                // Converter bitmap para JPEG com qualidade 80%
                val byteArrayOutputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
                val imageBytes = byteArrayOutputStream.toByteArray()
                
                // Enviar tamanho da imagem primeiro
                val dataOutputStream = DataOutputStream(outputStream)
                dataOutputStream.writeInt(imageBytes.size)
                
                // Enviar imagem
                outputStream.write(imageBytes)
                outputStream.flush()
                
                socket.close()
                
                withContext(Dispatchers.Main) {
                    textViewStatus.text = "Foto enviada com sucesso!\nTamanho: ${imageBytes.size / 1024}KB\nTimeout: 5s\nFormato: JPEG 80% qualidade, máx. 1280px\nAPIs modernas: ActivityResultLauncher + MediaStore"
                    buttonSendPhoto.isEnabled = true
                    Toast.makeText(this@MainActivity, "Foto enviada!", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: java.net.SocketTimeoutException) {
                withContext(Dispatchers.Main) {
                    textViewStatus.text = "Timeout: Servidor não respondeu em 5 segundos\nVerifique se o servidor está rodando\nFormato: JPEG 80% qualidade, máx. 1280px\nAPIs modernas: ActivityResultLauncher + MediaStore"
                    buttonSendPhoto.isEnabled = true
                    Toast.makeText(this@MainActivity, "Timeout: Servidor não respondeu", Toast.LENGTH_LONG).show()
                }
            } catch (e: java.net.ConnectException) {
                withContext(Dispatchers.Main) {
                    textViewStatus.text = "Erro de conexão: Servidor indisponível\nVerifique IP/Porta e se o servidor está rodando\nFormato: JPEG 80% qualidade, máx. 1280px\nAPIs modernas: ActivityResultLauncher + MediaStore"
                    buttonSendPhoto.isEnabled = true
                    Toast.makeText(this@MainActivity, "Erro: Servidor indisponível", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    textViewStatus.text = "Erro ao enviar: ${e.message}\nTimeout: 5s\nFormato: JPEG 80% qualidade, máx. 1280px\nAPIs modernas: ActivityResultLauncher + MediaStore"
                    buttonSendPhoto.isEnabled = true
                    Toast.makeText(this@MainActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}