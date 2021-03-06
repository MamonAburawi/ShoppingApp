package com.shoppingapp.info.repository.user

import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.shoppingapp.info.data.Product
import com.shoppingapp.info.utils.Result
import com.shoppingapp.info.data.User
import com.shoppingapp.info.utils.OrderStatus
import kotlinx.coroutines.tasks.await
import java.lang.IndexOutOfBoundsException

class RemoteUserRepository () {

    private val _root by lazy { FirebaseFirestore.getInstance() }
    private val mAuth by lazy { FirebaseAuth.getInstance() }

    enum class SignStatus{ LOADING, ERROR, DONE}

    private fun usersCollectionRef() = _root.collection(USERS_COLLECTION)

    suspend fun signOut(){ mAuth.signOut() }


    suspend fun getOrdersFromRemote(userId: String): List<User.OrderItem>? {
            val ref = usersCollectionRef().document(userId).get().await()
            val orders = if (ref != null) {
                val user = ref.toObject(User::class.java)
                return user?.orders
            } else { null }
        return orders
    }



    suspend fun insertOrder(order: User.OrderItem){
        // owner
        val ownerId = order.items[0].ownerId
        val ownerRef = usersCollectionRef().document(ownerId).get().await()
        if (ownerRef != null){
            usersCollectionRef().document(ownerId)
                .update(ORDERS_FIELD, FieldValue.arrayUnion(order))
        }

        // customer
        val customer = usersCollectionRef().document(order.customerId).get().await()
        if (customer != null){
            usersCollectionRef().document(ownerId)
                .update(ORDERS_FIELD, FieldValue.arrayUnion(order))
        }

    }


    suspend fun deleteOrder(order: User.OrderItem){
        // owner
        val ownerId = order.items[0].ownerId
        val ownerRef = usersCollectionRef().document(ownerId).get().await()
        if (ownerRef != null){
            usersCollectionRef().document(ownerId)
                .update(ORDERS_FIELD, FieldValue.arrayRemove(order))
        }

        // customer
        val customer = usersCollectionRef().document(order.customerId).get().await()
        if (customer != null){
            usersCollectionRef().document(ownerId)
                .update(ORDERS_FIELD, FieldValue.arrayRemove(order))
        }

    }



    fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential, isUserLoggedIn: MutableLiveData<Boolean>) {
        try {
            mAuth.signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "signInWithCredential:success")
                        val user = task.result?.user
                        if (user != null) {
                            isUserLoggedIn.postValue(true)
                        }

                    } else {
                        Log.w(TAG, "signInWithCredential:failure", task.exception)
                        if (task.exception is FirebaseAuthInvalidCredentialsException) {
                            Log.d(TAG, "createUserWithMobile:failure", task.exception)
                            isUserLoggedIn.postValue(false)

                        }
                    }
                }.addOnFailureListener {
                    Log.d(TAG, "createUserWithMobile:failure", it)
                    isUserLoggedIn.postValue(false)

                }
        } catch (e: Exception) {

        }
    }

    suspend fun getUserById(userId: String, onComplete:(User?) -> Unit){
        val resRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        if (resRef != null){
            val user = resRef.toObjects(User::class.java)[0]
            onComplete(user)
        }else{
            onComplete(null)
        }
    }

    suspend fun isEmailVerified() = mAuth.currentUser?.isEmailVerified

    suspend fun checkPassByUserId(userId: String ,password: String,onComplete: (Boolean) -> Unit) {
        val ref = usersCollectionRef().whereEqualTo(USER_ID_FIELD,userId).get().await()
        if (ref != null){
            try {
                val user = ref.toObjects(User::class.java)[0]
                Log.i("Login","email: ${user.email}")
                if (user.password == password){
                    onComplete(true)
                }else{
                    onComplete(false)
                }
            }catch (ex: IndexOutOfBoundsException){
                onComplete(false)
            }

        }
    }

    suspend fun checkUserIsExist(email: String, isExist:(Boolean) -> Unit, onError:(String) -> Unit) {
            FirebaseAuth.getInstance().fetchSignInMethodsForEmail(email).addOnCompleteListener {
                try {
                    val isUserExist = it.result.signInMethods?.isEmpty()
                    if (isUserExist!!){ // user is not exist
                        isExist(false)
                    }else{ // user is exist
                        isExist(true)
                    }
                }
                catch (ex: Exception){ // maybe there is another error..
//                    onError(context.resources.getString(R.string.no_connection))
                }
            }
    }



    suspend fun addUser(user: User) {
        usersCollectionRef()
            .document(user.userId)
            .set(user)
            .addOnSuccessListener {
                Log.d(TAG, "Doc added")
            }
            .addOnFailureListener { e ->
                Log.d(TAG, "firebase fire store error occurred: $e")
            }
    }

    suspend fun deleteUser(userId: String) { usersCollectionRef().document(userId).delete().await() }

    suspend fun getUser(userId: String): User? {
        return null
    }

    suspend fun getUserById(userId: String) = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
            .toObjects(User::class.java)[0]

    suspend fun getOrdersByUserId(userId: String): Result<List<User.OrderItem>?> {
        val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        return if (!userRef.isEmpty) {
            val userData = userRef.documents[0].toObject(User::class.java)
            Result.Success(userData!!.orders)
        } else {
            Result.Error(Exception("User Not Found!"))
        }
    }

    // return list of product id
    suspend fun getStuckLikes(userId: String, products: List<Product>): List<String> {
        var diff: List<String> = emptyList()
            val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
             if (!userRef.isEmpty){
                val userData = userRef.documents[0].toObject(User::class.java)
                val likes = userData?.likes
                val productsId = products.map { it.productId }
                diff = likes?.minus(productsId.toSet())!!

            }
        return diff
    }


    // return list of product id
    suspend fun getStuckCartItems(userId: String,products: List<Product>): List<String> {
        var diff: List<String> = emptyList()
        val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        if (!userRef.isEmpty){
            val userData = userRef.documents[0].toObject(User::class.java)
            val cart = userData?.cart?.map { it.productId }
            val productsId = products.map { it.productId }
            diff = cart?.minus(productsId.toSet())!!
        }
        return diff
    }

    // return list of orders id
    suspend fun getStuckOrdersIds(userId: String,orders: List<User.OrderItem>): List<String> {
        var diff: List<String> = emptyList()
        val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        if (!userRef.isEmpty){
            val userData = userRef.documents[0].toObject(User::class.java)
            val order = userData?.orders?.map { it.orderId }
            val orderId = orders.map { it.orderId }
            diff = order?.minus(orderId.toSet())!!
        }
        return diff
    }


    suspend fun getLikesByUserId(userId: String): Result<List<String>?> {
        val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        return if (!userRef.isEmpty) {
            val userData = userRef.documents[0].toObject(User::class.java)
            Result.Success(userData!!.likes)
        } else {
            Result.Error(Exception("User Not Found!"))
        }
    }

    suspend fun getUserByMobileAndPassword(mobile: String, password: String): MutableList<User> = usersCollectionRef().whereEqualTo(
        PHONE_FIELD, mobile)
            .whereEqualTo(PASSWORD_FIELD, password).get().await().toObjects(User::class.java)

    suspend fun likeProduct(productId: String, userId: String) {
        val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        if (!userRef.isEmpty) {
            val docId = userRef.documents[0].id
            usersCollectionRef().document(docId)
                .update(LIKES_FIELD, FieldValue.arrayUnion(productId))
        }
    }

    suspend fun dislikeProduct(productId: String, userId: String) {
        val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        if (!userRef.isEmpty) {
            val docId = userRef.documents[0].id
            usersCollectionRef().document(docId)
                .update(LIKES_FIELD, FieldValue.arrayRemove(productId))
        }
    }

    suspend fun insertCartItem(newItem: User.CartItem, userId: String) {
        val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        if (!userRef.isEmpty) {
            val docId = userRef.documents[0].id
            usersCollectionRef().document(docId)
                .update(CART_FIELD, FieldValue.arrayUnion(newItem.toHashMap()))
        }
    }

    suspend fun updateCartItem(item: User.CartItem, userId: String) {
        val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        if (!userRef.isEmpty) {
            val docId = userRef.documents[0].id
            val oldCart =
                userRef.documents[0].toObject(User::class.java)?.cart?.toMutableList()
            val idx = oldCart?.indexOfFirst { it.itemId == item.itemId } ?: -1
            if (idx != -1) {
                oldCart?.set(idx, item)
            }
            usersCollectionRef().document(docId)
                .update(CART_FIELD, oldCart?.map { it.toHashMap() })
        }
    }

    suspend fun deleteCartItem(itemId: String, userId: String) {
        val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        if (!userRef.isEmpty) {
            val docId = userRef.documents[0].id
            val oldCart =
                userRef.documents[0].toObject(User::class.java)?.cart?.toMutableList()
            val idx = oldCart?.indexOfFirst { it.itemId == itemId } ?: -1
            if (idx != -1) {
                oldCart?.removeAt(idx)
            }
            usersCollectionRef().document(docId)
                .update(CART_FIELD, oldCart?.map { it.toHashMap() })
        }
    }



    suspend fun placeOrder(newOrder: User.OrderItem, userId: String) {
        // add order to customer and
        // specific items to their owners
        // empty customers cart
        val ownerProducts: MutableMap<String, MutableList<User.CartItem>> = mutableMapOf()
        for (item in newOrder.items) {
            if (!ownerProducts.containsKey(item.ownerId)) {
                ownerProducts[item.ownerId] = mutableListOf()
            }
            ownerProducts[item.ownerId]?.add(item)
        }
        ownerProducts.forEach { (ownerId, items) ->
            run {
                val itemPrices = mutableMapOf<String, Double>()
                items.forEach { item ->
                    itemPrices[item.itemId] = newOrder.itemsPrices[item.itemId] ?: 0.0
                }
                val ownerOrder = User.OrderItem(
                    newOrder.orderId,
                    userId,
                    items,
                    itemPrices,
                    newOrder.shippingCharges,
                    newOrder.paymentMethod,
                    newOrder.address,
                    newOrder.orderDate,
                    newOrder.status)

                val ownerRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, ownerId).get().await()
                if (!ownerRef.isEmpty) {
                    val docId = ownerRef.documents[0].id
                    usersCollectionRef().document(docId)
                        .update(ORDERS_FIELD, FieldValue.arrayUnion(ownerOrder.toHashMap()))
                }
            }
        }

        val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        if (!userRef.isEmpty) {
            val docId = userRef.documents[0].id
            usersCollectionRef().document(docId)
                .update(ORDERS_FIELD, FieldValue.arrayUnion(newOrder.toHashMap()))
            usersCollectionRef().document(docId)
                .update(CART_FIELD, ArrayList<User.CartItem>())
        }
    }

    suspend fun setStatusOfOrderByUserId(orderId: String, userId: String, status: String) {
        // update on customer and owner
        val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        if (!userRef.isEmpty) {
            val docId = userRef.documents[0].id
            val ordersList =
                userRef.documents[0].toObject(User::class.java)?.orders?.toMutableList()
            val idx = ordersList?.indexOfFirst { it.orderId == orderId } ?: -1
            if (idx != -1) {
                val orderData = ordersList?.get(idx)
                if (orderData != null) {
                    usersCollectionRef().document(docId)
                        .update(ORDERS_FIELD, FieldValue.arrayRemove(orderData.toHashMap()))
                    orderData.status = status
                    usersCollectionRef().document(docId)
                        .update(ORDERS_FIELD, FieldValue.arrayUnion(orderData.toHashMap()))

                    // updating customer status
                    val custRef =
                        usersCollectionRef().whereEqualTo(USER_ID_FIELD, orderData.customerId)
                            .get().await()
                    if (!custRef.isEmpty) {
                        val did = custRef.documents[0].id
                        val orders =
                            custRef.documents[0].toObject(User::class.java)?.orders?.toMutableList()
                        val pos = orders?.indexOfFirst { it.orderId == orderId } ?: -1
                        if (pos != -1) {
                            val order = orders?.get(pos)
                            if (order != null) {
                                usersCollectionRef().document(did).update(
                                    ORDERS_FIELD,
                                    FieldValue.arrayRemove(order.toHashMap())
                                )
                                order.status = status
                                usersCollectionRef().document(did).update(
                                    ORDERS_FIELD,
                                    FieldValue.arrayUnion(order.toHashMap())
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun clearUser(userId: String) {
        usersCollectionRef().document(userId).delete()
    }


    companion object {

        private const val TAG = "RemoteUserRepository"

        private const val USERS_COLLECTION = "users"
        private const val USER_ID_FIELD = "userId"
        private const val LIKES_FIELD = "likes"
        private const val EMAIL_FIELD = "email"
        private const val CART_FIELD = "cart"
        private const val ORDERS_FIELD = "orders"
        private const val PHONE_FIELD = "phone"
        private const val PASSWORD_FIELD = "password"
        private const val EMAIL_MOBILE_DOC = "emailAndMobiles"
    }
}