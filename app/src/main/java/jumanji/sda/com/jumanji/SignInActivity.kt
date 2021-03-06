package jumanji.sda.com.jumanji

import android.app.Activity
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_sign_in.*


class SignInActivity : AppCompatActivity(), TextWatcher {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)

        signInButton.isEnabled = false
        userNameField.addTextChangedListener(this)
        passwordField.addTextChangedListener(this)

        profileSignUpButton.setOnClickListener {
            val createProfileIntent = Intent(this, CreateProfileActivity::class.java)
            startActivity(createProfileIntent)
            this.finish()
        }

        signInButton.setOnClickListener({
            signIn(userNameField.text.toString(), passwordField.text.toString())
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
            startActivityForResult(client.signInIntent, 10)
        })
    }

    private fun signIn(email: String, password: String) {
        val authenticator = FirebaseAuth.getInstance()
        authenticator.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, { task ->
                    if (task.isSuccessful) {
                        val intent = Intent(this, ProgramActivity::class.java)
                        startActivity(intent)
                        this.finish()
                    } else {
                        Toast.makeText(this, "something went wrong...", Toast.LENGTH_SHORT)
                                .show()
                    }
                })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 10 && resultCode == Activity.RESULT_OK) {
            val signedInAccountFromIntent = GoogleSignIn.getSignedInAccountFromIntent(data)
            if (signedInAccountFromIntent.isSuccessful) {
                val profileViewModel = ViewModelProviders.of(this)[ProfileViewModel::class.java]
                val database = FirebaseFirestore.getInstance()
                val userName = GoogleSignIn.getLastSignedInAccount(this)?.givenName.toString()
                Log.d(javaClass.simpleName + "Google account", "This is the current google account: $database")
                createNewStatisticIsNotExistsForGoogleAccountUser(database, profileViewModel, userName)
                getInfo(signedInAccountFromIntent)
                val intent = Intent(this, ProgramActivity::class.java)
                startActivity(intent)
                this.finish()
            }
        }
    }

    private fun createNewStatisticIsNotExistsForGoogleAccountUser(database: FirebaseFirestore,
                                                                  profileViewModel: ProfileViewModel,
                                                                  userName: String) {
        val taskToGetDocument = database.collection("userStatistics")
                .document(userName)
                .get()
        taskToGetDocument.addOnCompleteListener { task ->
            if (!task.result.exists()) {
                profileViewModel.initializeUserPinNumber(userName)
                val statisticViewModel = ViewModelProviders.of(this)[StatisticViewModel::class.java]
                statisticViewModel.updateCommunityStatistics(StatisticRepository.TOTAL_USERS)
            }
        }
    }

    private fun getInfo(info: Task<GoogleSignInAccount>) {
        val result = info.result
        Toast.makeText(this, "Welcome  " + result.displayName, Toast.LENGTH_LONG).show()
    }

    override fun afterTextChanged(s: Editable?) {
        signInButton.isEnabled = userNameField.text.isNotEmpty() && passwordField.text.isNotEmpty()
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
}
