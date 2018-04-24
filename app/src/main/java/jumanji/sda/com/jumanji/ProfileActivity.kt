package jumanji.sda.com.jumanji

import android.content.Intent
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_profile.*


class ProfileActivity : AppCompatActivity() {

    val userName = ""
    val email = ""
    var uri: Uri? = null


//      imageView.setOnClickListener({
//      })

    private val intentPickImage = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

    override fun startActivityForResult(intent: Intent?, requestCode: Int) {
        super.startActivityForResult(intentPickImage, 0)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        uri = data?.data

    }


}

