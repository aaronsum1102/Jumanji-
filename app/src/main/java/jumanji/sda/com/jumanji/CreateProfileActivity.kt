package jumanji.sda.com.jumanji

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.activity_create_profile.*
import java.io.*

interface OnNewUserRegisteredCallback {
    fun onProfileSaveToFirebase()
}

class CreateProfileActivity : AppCompatActivity(), TextWatcher, PhotoListener, OnNewUserRegisteredCallback {
    companion object {
        private const val REQUEST_CAMERA = 100
        private const val SELECT_FILE = 200
    }

    val profileViewModel by lazy { ViewModelProviders.of(this)[ProfileViewModel::class.java] }
    var userChoosenTask: String = ""

    private lateinit var profilePhotoUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_profile)

        val username = profileViewModel.userInfo?.value?.userName
        val email = profileViewModel.userInfo?.value?.email

        if (username != "null") userNameField.setText(username)
        if (email != "null") emailField.setText(email)

        saveButton.isEnabled = false
        userNameField.addTextChangedListener(this)
        passwordField.addTextChangedListener(this)
        confirmPasswordField.addTextChangedListener(this)
        emailField.addTextChangedListener(this)

        profilePhoto.setOnClickListener {
            selectImage()
        }

        saveButton.setOnClickListener {
            if (passwordField.text.length >= 6) {
                val userName = userNameField.text.toString()
                val email = emailField.text.toString()
                val password = passwordField.text.toString()
                val photoRepository = PhotoRepository(email)
                Toast.makeText(this, "creating your profile now...", Toast.LENGTH_SHORT).show()
                if (this::profilePhotoUri.isInitialized) {
                    val orientationFromPhoto = UtilCamera.getMetadataFromPhoto(profilePhotoUri,
                            applicationContext)
                            .orientation
                    val resizeImage = UtilCamera.resizeImage(profilePhotoUri, applicationContext)
                    resizeImage?.let {
                        val internalUri = UtilCamera.getUriForFile(resizeImage, applicationContext)
                        orientationFromPhoto?.let {
                            val profile = UserProfile(userName, email, internalUri.toString(), orientationFromPhoto)
                            profileViewModel.saveUserProfile(profile, password, this, this)
                                    .continueWith {
                                        val newProfile = profile.copy(userId = it.result.user.uid)
                                        photoRepository.storePhotoToDatabase(internalUri, this)
                                                .addOnSuccessListener { uploadTask ->
                                                    uploadTask.storage.downloadUrl
                                                            .addOnSuccessListener {
                                                                profileViewModel.storeProfileDataToDB(newProfile)
                                                            }
                                                }
                                    }
                        }
                    }
                }
                saveButton.isEnabled = false
            } else {
                Toast.makeText(this@CreateProfileActivity,
                        "Password is too short.",
                        Toast.LENGTH_SHORT)
                        .show()
            }
        }

        cancelButton.setOnClickListener({
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            this.finish()
        })
    }

    override fun onBackPressed() {
        val intent = Intent(this, SignInActivity::class.java)
        startActivity(intent)
        this.finish()
    }

    override fun selectImage() {
        val items = arrayOf<CharSequence>("Take Photo", "Choose from Library", "Cancel")
        val builder = AlertDialog.Builder(this@CreateProfileActivity)
        builder.setTitle("Take Photo")
        builder.setItems(items, { dialog, item ->
            when (items[item]) {
                "Take Photo" -> {
                    userChoosenTask = "Take Photo"
                    UtilCamera.checkPermissionBeforeAction(context = this@CreateProfileActivity)
                }

                "Choose from Library" -> {
                    userChoosenTask = "Choose from Library"
                    UtilCamera.checkPermissionBeforeAction(context = this@CreateProfileActivity)
                }

                "Cancel" -> dialog.dismiss()
            }
        })
        builder.show()
    }

    private fun cameraIntent() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, REQUEST_CAMERA)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            UtilCamera.PERMISSIONS_TO_READ_EXTERNAL_STORAGE ->
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    UtilCamera.chooseFromGallery(null, applicationContext)
                } else {
                    UtilCamera.displayMessageForNoPermission(null, applicationContext)
                }
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            when (requestCode) {
                SELECT_FILE -> {
                    uri?.let {
                        profilePhotoUri = uri
                        try {
                            val orientationFromPhoto = UtilCamera.getMetadataFromPhoto(uri,
                                    applicationContext).orientation?.toInt()
                            orientationFromPhoto?.let {
                                UtilCamera.loadPhotoIntoView(uri, orientationFromPhoto, profilePhoto)
                            }

                        } catch (exception: NumberFormatException) {
                        }
                    }
                }
                REQUEST_CAMERA -> {
                    onCaptureImageResult(data)
                }
            }
        }
    }

    private fun onCaptureImageResult(data: Intent?) {
        val thumbnail = data?.extras?.get("data") as Bitmap
        val bytes = ByteArrayOutputStream()
        thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, bytes)
        val destination = File(Environment.getExternalStorageDirectory(),
                System.currentTimeMillis().toString() + ".jpg")
        val fo: FileOutputStream
        try {
            destination.createNewFile()
            fo = FileOutputStream(destination)
            fo.write(bytes.toByteArray())
            fo.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        profilePhoto.setImageBitmap(thumbnail)
        profilePhotoUri = Uri.fromFile(destination.absoluteFile)
    }

    override fun onProfileSaveToFirebase() {
        val viewModel = ViewModelProviders.of(this)[StatisticViewModel::class.java]
        viewModel.updateCommunityStatistics(StatisticRepository.TOTAL_USERS)

        val intent = Intent(this, ProgramActivity::class.java)
        startActivity(intent)
        this.finish()
    }

    override fun afterTextChanged(s: Editable?) {
        if (userNameField.text.isNotEmpty() &&
                passwordField.text.isNotEmpty() &&
                confirmPasswordField.text.isNotEmpty() &&
                passwordField.text.toString() == confirmPasswordField.text.toString() &&
                emailField.text.contains("@")) {
            saveButton.isEnabled = true
        } else {
            if (passwordField.text.length == confirmPasswordField.text.length &&
                    passwordField.text.toString() != confirmPasswordField.text.toString()) {
                Toast.makeText(this,
                        "Password doesn't match.",
                        Toast.LENGTH_SHORT)
                        .show()
            }
            saveButton.isEnabled = false
        }
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
}
