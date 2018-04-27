package jumanji.sda.com.jumanji

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import kotlinx.android.synthetic.main.activity_sign_in.*


class SignInActivity : AppCompatActivity() {

    val repository = UserProfileRepository()
    var userName = ""
    var email = ""
    var uriString = ""

    var autentificator = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)

        profileSignUpButton.setOnClickListener {
            val createProfileIntent = Intent(this, CreateProfileActivity::class.java)
            startActivity(createProfileIntent)
        }

        signInButton.setOnClickListener({

            signIn(it, userNameField.text.toString(), passwordField.text.toString())

        })

        googleSignInButton.setOnClickListener({

            //Sign in with google
            // Configure sign-in to request the user's ID, email address, and basic
            // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestProfile()
                    .build()


            // Build a GoogleSignInClient with the options specified by options.

            val client = GoogleSignIn.getClient(this, options)

            val signIn = client.silentSignIn()

            if (signIn.isSuccessful) {
                getInfo(signIn)

            } else {
                startActivityForResult(client.signInIntent, 10)
            }

            val intent = Intent(this, ProgramActivity::class.java )
            startActivity(intent)
        })

    }

    private fun signIn(view: View, email: String, password: String) {
        autentificator.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener (this, { task ->
                    if(task.isSuccessful) {
                        val intent = Intent(this, ProgramActivity::class.java)
                        // TODO add an ID or delete next line
                        //intent.putExtra(id, autentificator.currentUser?.email)
                        startActivity(intent)
                    } else {
                        Toast.makeText (this, "something went wrong...", Toast.LENGTH_SHORT)
                                .show()
                }
        })

    }


    /* override fun onStart() {
         super.onStart()
         // Check for existing Google Sign In account, if the user is already signed in
         // the GoogleSignInAccount will be non-null.
         val account = GoogleSignIn.getLastSignedInAccount(this)
         //updateUI(account)
     }*/

    /* private fun updateUI(account: GoogleSignInAccount?) {
         if (account != null) {

             val intent = Intent(this, ProgramActivity::class.java)

         } else {

             //no last log in
             val intent = Intent(this, SignInActivity::class.java)
         }
         startActivity(intent)


     }
 */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 10 && resultCode == Activity.RESULT_OK) {
            val signedInAccountFromIntent = GoogleSignIn.getSignedInAccountFromIntent(data)
            if (signedInAccountFromIntent.isSuccessful) {
                getInfo(signedInAccountFromIntent)
            }
        }
    }

    private fun getInfo(info: Task<GoogleSignInAccount>) {
        val result = info.result
        Toast.makeText(this, "Welcome  " + result.displayName, Toast.LENGTH_LONG).show()

        userName = result.givenName!!
        email = result.email!!
        uriString = result.photoUrl.toString()

        val profile = UserProfile(userName, email, uriString)
        repository.storeToDatabase(profile)

    }
}
