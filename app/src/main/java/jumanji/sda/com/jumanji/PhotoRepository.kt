package jumanji.sda.com.jumanji

import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class PhotoRepository(email: String?) {

    val email = email?.toLowerCase()

    fun storePhotoToDatabase(uri: Uri?, activity: androidx.fragment.app.FragmentActivity?, callback: OnUrlAvailableCallback, toastFlag: Boolean) {
        //var uri = data?.data
        val mStorageRef: StorageReference = FirebaseStorage.getInstance().getReference("$email/images")
        val imageRef = mStorageRef.child("$uri")
        Log.e("value", "uri Value: $uri")

        if (uri != null) {
            imageRef.putFile(uri)
                    .addOnSuccessListener { taskSnapshot ->
                        // Get a URL to the uploaded content
                        taskSnapshot.storage.downloadUrl
                                .addOnSuccessListener {
                                    val downloadUrl = it
                                    Log.d("SUCCESS", "Able  to upload")
                                    if (toastFlag) {
                                        val toast = Toast.makeText(activity, "File Uploaded ", Toast.LENGTH_SHORT)
                                        toast.show()
                                    } else {
                                        //can add toast to say Profile saved.
                                    }
                                    if (downloadUrl != null) {
                                        callback.storeDataToFirebase(downloadUrl)
                                    }
                                }

                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(activity, exception.message, Toast.LENGTH_SHORT).show()
                        Log.d("ERROR", "Unable to upload")
                    }

        } else {
            Toast.makeText(activity, "File not found ", Toast.LENGTH_SHORT).show()
        }
    }
}