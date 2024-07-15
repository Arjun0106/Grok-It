package com.example.geminiproject

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.geminiproject.databinding.ActivityMainBinding
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var bitmap: Bitmap
    private lateinit var imageUri: Uri

    lateinit var providesRealTimeDatabaseInstance: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        providesRealTimeDatabaseInstance = FirebaseDatabase.getInstance().getReference("Product Information")

        binding.save.setOnClickListener {
            val product = setProductRequest()
            dbCreation(product)
        }

        binding.predButton.setOnClickListener {
            resetUI()
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                imageUri = createImageUri()!!
                imageUri.let { uri ->
                    contract.launch(uri)
                }
            } else {
                requestPermission.launch(android.Manifest.permission.CAMERA)
            }
        }

        binding.selectImage.setOnClickListener {
            selectImageFromStorage.launch("image/*")
        }
    }

    private fun dbCreation(product: Details) {
        providesRealTimeDatabaseInstance.child(product.uniqueId!!).setValue(product).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this@MainActivity, "Stored the data in Firebase Successfully!", Toast.LENGTH_LONG).show()
                resetUI()
            } else {
                Toast.makeText(this@MainActivity, "Unable to store the data in Firebase!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            imageUri = createImageUri()!!
            imageUri.let { uri ->
                contract.launch(uri)
            }
        } else {
            Toast.makeText(this, "Permission Denied!! Try Again", Toast.LENGTH_SHORT).show()
        }
    }

    private val contract = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            bitmap = uriToBitmap(imageUri)
            binding.predimage.setImageBitmap(bitmap)
            binding.predimage.rotation = 90f
            binding.predimage.background = null
            processImage(bitmap)
        } else {
            Toast.makeText(this, "Failed to Capture Image", Toast.LENGTH_SHORT).show()
        }
    }

    private val selectImageFromStorage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            bitmap = uriToBitmap(it)
            binding.predimage.setImageBitmap(bitmap)
            binding.predimage.rotation = 90f
            binding.predimage.background = null
            processImage(bitmap)
        } ?: run {
            Toast.makeText(this, "Failed to Select Image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageUri(): Uri? {
        val image = File(applicationContext.filesDir, "camera_photo_gemini.png")
        return FileProvider.getUriForFile(
            applicationContext,
            "com.example.geminiproject.fileProvider",
            image
        )
    }

    private fun uriToBitmap(uri: Uri): Bitmap {
        val contentResolver = contentResolver
        val inputStream = contentResolver.openInputStream(uri)
        val bitmaps = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        return bitmaps
    }

    private fun processImage(image: Bitmap) {
        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = "enter_your_api_here" //Don't forget to provide API Key
        )

        val inputContent = content {
            image(image)
            text("Scan the image and provide the details of the product in points: 1)Product Name, 2)Description, 3)Colour, 4)Pattern, 5)Brand, 6)Price, 7)Category, 8)Size, 9)Material, and 10)Weight.")
        }

        lifecycleScope.launch {
            try {
                val response = generativeModel.generateContent(inputContent)
                parseResponse(response.text.toString())
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Failed to Generate Content", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun parseResponse(response: String) {
        val lines = response.split("\n")
        var productName = ""
        var description = ""
        var color = ""
        var pattern = ""
        var brand = ""
        var price = ""
        var category = ""
        var size = ""
        var material = ""
        var weight = ""

        for (line in lines) {
            when {
                line.contains("Product Name:", true) -> productName = "Object Name :\n\n" + line.substringAfter("Product Name:").trim().trimStart('*')
                line.contains("Description:", true) -> description = "Object Description :\n\n" + line.substringAfter("Description:").trim().trimStart('*')
                line.contains("Colour:", true) -> color = "Object Colour :\n\n" + line.substringAfter("Colour:").trim().trimStart('*')
                line.contains("Pattern:", true) -> pattern = "Object Pattern :\n\n" + line.substringAfter("Pattern:").trim().trimStart('*')
                line.contains("Brand:", true) -> brand = "Object Brand (If Applicable) :\n\n" + line.substringAfter("Brand:").trim().trimStart('*')
                line.contains("Price:", true) -> price = "Object Price (If Applicable) :\n\n" + line.substringAfter("Price:").trim().trimStart('*')
                line.contains("Category:", true) -> category = "Object Category :\n\n" + line.substringAfter("Category:").trim().trimStart('*')
                line.contains("Size:", true) -> size = "Object Size :\n\n" + line.substringAfter("Size:").trim().trimStart('*')
                line.contains("Material:", true) -> material = "Object Material :\n\n" + line.substringAfter("Material:").trim().trimStart('*')
                line.contains("Weight:", true) -> weight = "Object Weight (If Applicable) :\n\n" + line.substringAfter("Weight:").trim().trimStart('*')
            }
        }

        binding.productName.text = productName
        binding.description.text = description
        binding.color.text = color
        binding.pattern.text = pattern
        binding.brand.text = brand
        binding.price.text = price
        binding.category.text = category
        binding.size.text = size
        binding.material.text = material
        binding.weight.text = weight
        binding.predButton.text = "Live Scan"
        binding.save.isVisible = true
        toggleResultsVisibility(true)
    }

    private fun setProductRequest(): Details {
        val product = binding.productName.text.toString()
        val description = binding.description.text.toString()
        val colour = binding.color.text.toString()
        val pattern = binding.pattern.text.toString()
        val brand = binding.brand.text.toString()
        val price = binding.price.text.toString()
        val category = binding.category.text.toString()
        val size = binding.size.text.toString()
        val material = binding.material.text.toString()
        val weight = binding.weight.text.toString()
        val uniqueId = providesRealTimeDatabaseInstance.push().key
        return Details(
            product = product,
            description = description,
            colour = colour,
            pattern = pattern,
            brand = brand,
            price = price,
            category = category,
            size = size,
            material = material,
            weight = weight,
            uniqueId = uniqueId
        )
    }

    private fun resetUI() {
        binding.predimage.setImageBitmap(null)
        binding.predimage.setBackgroundResource(R.drawable.bg_img)
        binding.predimage.rotation = 0f
        binding.productName.text = ""
        binding.description.text = ""
        binding.color.text = ""
        binding.pattern.text = ""
        binding.brand.text = ""
        binding.price.text = ""
        binding.category.text = ""
        binding.size.text = ""
        binding.material.text = ""
        binding.weight.text = ""
        binding.save.isVisible = false
        binding.predButton.text = "Processing...."
        toggleResultsVisibility(false)
    }

    private fun toggleResultsVisibility(showResults: Boolean) {
        binding.scanHint.isVisible = !showResults
        binding.productName.isVisible = showResults
        binding.description.isVisible = showResults
        binding.color.isVisible = showResults
        binding.pattern.isVisible = showResults
        binding.brand.isVisible = showResults
        binding.price.isVisible = showResults
        binding.category.isVisible = showResults
        binding.size.isVisible = showResults
        binding.material.isVisible = showResults
        binding.weight.isVisible = showResults
    }
}
