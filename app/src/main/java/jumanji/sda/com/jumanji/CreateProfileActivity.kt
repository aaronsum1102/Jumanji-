package jumanji.sda.com.jumanji

import android.app.Activity
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_create_profile.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

interface OnNewUserRegisteredCallback {
    fun onProfileSaveToFirebase()
}

class CreateProfileActivity : AppCompatActivity(), TextWatcher, PhotoListener, OnUrlAvailableCallback, OnNewUserRegisteredCallback, OnPermissionGrantedCallback {

    override fun storeDataToFirebase(uri: Uri) {
    }

    companion object {
        private const val REQUEST_CAMERA = 100
        private const val SELECT_FILE = 200
    }

    val profileViewModel by lazy { ViewModelProviders.of(this)[ProfileViewModel::class.java] }
    var userChoosenTask: String = ""

    private var uriString: Uri = Uri.parse("android.resource://jumanji.sda.com.jumanji/" + R.drawable.download)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_profile)

        val username = profileViewModel.userInfo?.value?.userName
        val email = profileViewModel.userInfo?.value?.email

        if (username != "null") userNameField.setText(username)
        passwordField.setText(profileViewModel.userInfo?.value?.password)
        confirmPasswordField.setText(profileViewModel.userInfo?.value?.password)
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

                val profile = UserProfile(userName, password, email, uriString?.toString())
                profileViewModel.saveUserProfile(profile, this, this)

                Toast.makeText(this, "creating your profile now...", Toast.LENGTH_SHORT).show()
                photoRepository.storePhotoToDatabase(uriString, this, this, false)

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
                    Utility.checkPermission(context = this@CreateProfileActivity, callback = this)
                }

                "Choose from Library" -> {
                    userChoosenTask = "Choose from Library"
                    Utility.checkPermission(context = this@CreateProfileActivity, callback = this)
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

    private fun galleryIntent() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select File"), SELECT_FILE)
    }

    override fun actionWithPermission(context: Context) {
        when (userChoosenTask) {
            "Take Photo" -> cameraIntent()
            "Choose from Library" -> galleryIntent()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            Utility.MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE ->
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    actionWithPermission(this)
                } else {
                    Toast.makeText(this,
                            "I need your permission to show you a nice profile picture",
                            Toast.LENGTH_LONG)
                            .show()
                }
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            SELECT_FILE -> {
                if (resultCode == Activity.RESULT_OK) {
                    onSelectFromGalleryResult(data)
                }
            }

            REQUEST_CAMERA -> {
                if (resultCode == Activity.RESULT_OK) {
                    onCaptureImageResult(data)
                }
            }
        }
    }

    private fun onSelectFromGalleryResult(data: Intent?) {

        var bm: Bitmap? = null
        if (data != null) {
            try {
                bm = MediaStore.Images.Media.getBitmap(applicationContext.contentResolver, data.data)
                uriString = data.data
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        profilePhoto.setImageBitmap(bm)
        Log.d(javaClass.simpleName, "Setting image to profile.")
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
        uriString = Uri.fromFile(destination.absoluteFile)
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
