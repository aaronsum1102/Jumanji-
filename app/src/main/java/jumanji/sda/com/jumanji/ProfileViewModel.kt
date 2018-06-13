package jumanji.sda.com.jumanji

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.UserInfo
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProfileRepository(application)

    val reportedPins: MutableLiveData<String> = repository.reportedPins
    val cleanedPins: MutableLiveData<String> = repository.cleanedPins
    val userInfo: MutableLiveData<UserProfile>? = repository.userInfo

    fun saveUserProfile(profile: UserProfile, password: String, callback: OnNewUserRegisteredCallback, context: Context) : Task<AuthResult> {
        return repository.createNewUser(profile, password, callback, context)
    }

    fun storeProfileDataToDB(userProfile: UserProfile) {
        repository.storeProfileInfoToDB(userProfile)
    }

    fun checkIfUserSignedIn(context: Context) {
        Single.fromCallable { repository.signOut(context) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe()
    }

    fun signOut(){
        repository.signOut(getApplication())
    }

    fun deleteUserProfile(username: String?, context: Context?): Boolean {
        val result = Single.fromCallable { repository.userDelete(username, context) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe()
        Log.d(javaClass.simpleName, "DISPOSABLE result: " + result.isDisposed)
        return result.isDisposed.equals(true)
    }

    fun initializeUserPinNumber(user: String) {
        Single.fromCallable { repository.initializeUserPinNumber(user) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()).subscribe()
    }

    fun updateUserPinNumber(user: String) {
        Single.fromCallable { repository.updateUserPinNumber(user) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()).subscribe()
    }

    fun updateUserCleanedPinNumber(user: String) {
        Single.fromCallable { repository.updateUserCleanedPinNumber(user) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()).subscribe()
    }

    fun updateUserStatistics(user: String) {
        Single.fromCallable { repository.updateUserStatistics(user) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()).subscribe()
    }
}


