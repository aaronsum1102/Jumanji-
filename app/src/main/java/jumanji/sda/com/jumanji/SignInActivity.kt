package jumanji.sda.com.jumanji

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import kotlinx.android.synthetic.main.activity_sign_in.*

class SignInActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)
        profileSignUpButton.setOnClickListener { this.createProfile(it) }


    }

    fun createProfile(view: View){
        val profileIntent = Intent(this, ProfileActivity::class.java)
        startActivity(profileIntent)
    }
}
