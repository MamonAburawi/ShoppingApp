package com.shoppingapp.info.repository

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.shoppingapp.info.UserDataSource
import com.shoppingapp.info.data.UserData
import com.shoppingapp.info.utils.ShoppingAppSessionManager
import com.shoppingapp.info.utils.UserType
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import com.shoppingapp.info.Result

class AuthRepository(
	private val userLocalDataSource: UserDataSource,
	private val authRemoteDataSource: UserDataSource,
	private var sessionManager: ShoppingAppSessionManager
) : AuthRepoInterface {

	private var firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

	companion object {
		private const val TAG = "AuthRepository"
	}

	override fun getFirebaseAuth() = firebaseAuth

	override fun isRememberMeOn() = sessionManager.isRememberMeOn()

	override suspend fun refreshData() {
//		Log.d(TAG, "refreshing userdata")
//		if (sessionManager.isLoggedIn()) {
//			updateUserInLocalSource(sessionManager.getPhoneNumber())
//		} else {
//			sessionManager.logoutFromSession()
//			deleteUserFromLocalSource()
//		}
	}

	override suspend fun signUp(userData: UserData) {
		val isSeller = userData.userType == UserType.SELLER.name
		sessionManager.createLoginSession(
			userData.userId,
			userData.name,
			userData.phone,
			false,
			isSeller
		)
		Log.d(TAG, "on SignUp: Updating user in Local Source")
		userLocalDataSource.addUser(userData)
		Log.d(TAG, "on SignUp: Updating userdata on Remote Source")
		authRemoteDataSource.addUser(userData)
//
	}

	override suspend fun login(userData: UserData, rememberMe: Boolean) {
		val isSeller = userData.userType == UserType.SELLER.name
		sessionManager.createLoginSession(
			userData.userId,
			userData.name,
			userData.phone,
			rememberMe,
			isSeller
		)

		userLocalDataSource.addUser(userData)
	}


//	override suspend fun checkLogin(mobile: String, password: String): UserData? {
////		Log.d(TAG, "on Login: checking mobile and password")
////		var queryResult = mutableListOf<UserData>()
////		try {
////			queryResult = authRemoteDataSource.getUserByMobileAndPassword(mobile, password)
////		} catch (e: Exception) {
////			// No Handling
////		}
////		return if (queryResult.size > 0) {
////			queryResult[0]
////		} else {
////			null
////		}
//	}

	override fun signInWithPhoneAuthCredential(
		credential: PhoneAuthCredential,
		isUserLoggedIn: MutableLiveData<Boolean>, context: Context
	) {
//		try {
//			firebaseAuth.signInWithCredential(credential)
//				.addOnCompleteListener { task ->
//					if (task.isSuccessful) {
//						Log.d(TAG, "signInWithCredential:success")
//						val user = task.result?.user
//						if (user != null) {
//							isUserLoggedIn.postValue(true)
//						}
//
//					} else {
//						Log.w(TAG, "signInWithCredential:failure", task.exception)
//						if (task.exception is FirebaseAuthInvalidCredentialsException) {
//							Log.d(TAG, "createUserWithMobile:failure", task.exception)
//							isUserLoggedIn.postValue(false)
//							makeErrToast("Wrong OTP!", context)
//						}
//					}
//				}.addOnFailureListener {
//					Log.d(TAG, "createUserWithMobile:failure", it)
//					isUserLoggedIn.postValue(false)
//					makeErrToast("Invalid Request!", context)
//				}
//		} catch (e: Exception) {
//			makeErrToast("Some Error Occurred", context)
//		}
	}

	override suspend fun signOut() {
//		sessionManager.logoutFromSession()
//		firebaseAuth.signOut()
//		userLocalDataSource.clearUser()
	}

	override suspend fun deleteUser() {
		userLocalDataSource.deleteUser()
	}



	private fun makeErrToast(text: String, context: Context) {
		Toast.makeText(context, text, Toast.LENGTH_LONG).show()
	}

	private suspend fun deleteUserFromLocalSource() {
//		userLocalDataSource.clearUser()
	}

	private suspend fun updateUserInLocalSource(phoneNumber: String?) {
//		coroutineScope {
//			launch {
//				if (phoneNumber != null) {
//					val getUser = userLocalDataSource.getUserByMobile(phoneNumber)
//					if (getUser == null) {
//						userLocalDataSource.clearUser()
//						val uData = authRemoteDataSource.getUserByMobile(phoneNumber)
//						if (uData != null) {
//							userLocalDataSource.addUser(uData)
//						}
//					}
//				}
//			}
//		}
	}



	override suspend fun hardRefreshUserData() {
		val userId = sessionManager.getUserIdFromSession()
		if (userId != null) {
			userLocalDataSource.clearUser(userId)
			val uData = authRemoteDataSource.getUserById(userId)
			if (uData != null) {
				userLocalDataSource.addUser(uData)
			}
		}
	}



	override suspend fun insertProductToLikes(
		productId: String,
		userId: String
	): Result<Boolean> {
		return supervisorScope {
			val remoteRes = async {
				Log.d(TAG, "onLikeProduct: adding product to remote source")
				authRemoteDataSource.likeProduct(productId, userId)
			}
			val localRes = async {
				Log.d(TAG, "onLikeProduct: updating product to local source")
				userLocalDataSource.likeProduct(productId, userId)
			}
			try {
				localRes.await()
				remoteRes.await()
				Result.Success(true)
			} catch (e: Exception) {
				Result.Error(e)
			}
		}
	}


	override suspend fun removeProductFromLikes(
		productId: String,
		userId: String
	): Result<Boolean> {
		return supervisorScope {
			val remoteRes = async {
				Log.d(TAG, "onDislikeProduct: deleting product from remote source")
				authRemoteDataSource.dislikeProduct(productId, userId)
			}
			val localRes = async {
				Log.d(TAG, "onDislikeProduct: updating product to local source")
				userLocalDataSource.dislikeProduct(productId, userId)
			}
			try {
				localRes.await()
				remoteRes.await()
				Result.Success(true)
			} catch (e: Exception) {
				Result.Error(e)
			}
		}
	}



	override suspend fun insertCartItemByUserId(
		cartItem: UserData.CartItem,
		userId: String
	): Result<Boolean> {
		return supervisorScope {
			val remoteRes = async {
				Log.d(TAG, "onInsertCartItem: adding item to remote source")
				authRemoteDataSource.insertCartItem(cartItem, userId)
			}
			val localRes = async {
				Log.d(TAG, "onInsertCartItem: updating item to local source")
				userLocalDataSource.insertCartItem(cartItem, userId)
			}
			try {
				localRes.await()
				remoteRes.await()
				Result.Success(true)
			} catch (e: Exception) {
				Result.Error(e)
			}
		}
	}

	override suspend fun updateCartItemByUserId(
		cartItem: UserData.CartItem,
		userId: String
	): Result<Boolean> {
		return supervisorScope {
			val remoteRes = async {
				Log.d(TAG, "onUpdateCartItem: updating cart item on remote source")
				authRemoteDataSource.updateCartItem(cartItem, userId)
			}
			val localRes = async {
				Log.d(TAG, "onUpdateCartItem: updating cart item on local source")
				userLocalDataSource.updateCartItem(cartItem, userId)
			}
			try {
				localRes.await()
				remoteRes.await()
				Result.Success(true)
			} catch (e: Exception) {
				Result.Error(e)
			}
		}
	}

	override suspend fun deleteCartItemByUserId(itemId: String, userId: String): Result<Boolean> {
		return supervisorScope {
			val remoteRes = async {
				Log.d(TAG, "onDelete: deleting cart item from remote source")
				authRemoteDataSource.deleteCartItem(itemId, userId)
			}
			val localRes = async {
				Log.d(TAG, "onDelete: deleting cart item from local source")
				userLocalDataSource.deleteCartItem(itemId, userId)
			}
			try {
				localRes.await()
				remoteRes.await()
				Result.Success(true)
			} catch (e: Exception) {
				Result.Error(e)
			}
		}
	}

	override suspend fun placeOrder(newOrder: UserData.OrderItem, userId: String): Result<Boolean> {
		return supervisorScope {
			val remoteRes = async {
				Log.d(TAG, "onPlaceOrder: adding item to remote source")
				authRemoteDataSource.placeOrder(newOrder, userId)
			}
			val localRes = async {
				Log.d(TAG, "onPlaceOrder: adding item to local source")
				authRemoteDataSource.getUserById(userId){ user ->
					async {
						userLocalDataSource.clearUser(user!!.userId)
					}
					async {
						if (user != null) {
							userLocalDataSource.addUser(user)
						}
					}
				}
			}
			try {
				remoteRes.await()
				localRes.await()
				Result.Success(true)
			} catch (e: Exception) {
				Result.Error(e)
			}
		}
		return Result.Success(true)
	}


	override suspend fun setStatusOfOrder(
		orderId: String,
		userId: String,
		status: String
	): Result<Boolean> {
		return supervisorScope {
			val remoteRes = async {
				Log.d(TAG, "onSetStatus: updating status on remote source")
				authRemoteDataSource.setStatusOfOrderByUserId(orderId, userId, status)
			}
			val localRes = async {
				Log.d(TAG, "onSetStatus: updating status on local source")
				userLocalDataSource.setStatusOfOrderByUserId(orderId, userId, status)
			}
			try {
				localRes.await()
				remoteRes.await()
				Result.Success(true)
			} catch (e: Exception) {
				Result.Error(e)
			}
		}
	}

	override suspend fun getOrdersByUserId(userId: String): Result<List<UserData.OrderItem>?> {
		return userLocalDataSource.getOrdersByUserId(userId)
	}

//	override suspend fun getAddressesByUserId(userId: String): Result<List<UserData.Address>?> {
//		return userLocalDataSource.getAddressesByUserId(userId)
//	}

	override suspend fun getLikesByUserId(userId: String): Result<List<String>?> {
		return userLocalDataSource.getLikesByUserId(userId)
	}

//	suspend fun getUserData(userId: String): UserData? = userLocalDataSource.getUserById(userId)

	override suspend fun getUserDataById(userId: String, onComplete: (UserData?) -> Unit) {
		userLocalDataSource.getUserById(userId, onComplete)
	}


//	override suspend fun getUserData(userId: String): Result<UserData?> {
//
//	}

//	override suspend fun getUserData(userId: String): Result<UserData?> {
//		return userLocalDataSource.getUserById(userId)
//	}

}