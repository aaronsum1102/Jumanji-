package jumanji.sda.com.jumanji

import android.net.Uri
import android.widget.Toast
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask

class PhotoRepository(email: String?) {

    val email = email?.toLowerCase()

    fun storePhotoToDatabase(uri: Uri, activity: androidx.fragment.app.FragmentActivity?): UploadTask {
        val mStorageRef: StorageReference = FirebaseStorage.getInstance().getReference("$email/images")
        val imageRef = mStorageRef.child(uri.lastPathSegment)
        val uploadTask = imageRef.putFile(uri)
        uploadTask.addOnSuccessListener { taskSnapshot ->
            taskSnapshot.storage.downloadUrl
                    .addOnFailureListener { exception ->
                        Toast.makeText(activity, exception.message, Toast.LENGTH_SHORT).show()
                    }
        }
        return uploadTask
    }

}