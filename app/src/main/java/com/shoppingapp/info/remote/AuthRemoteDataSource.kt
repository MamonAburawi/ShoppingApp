package com.shoppingapp.info.remote

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.shoppingapp.info.R
import com.shoppingapp.info.Result
import com.shoppingapp.info.UserDataSource
import com.shoppingapp.info.data.UserData
import com.shoppingapp.info.utils.OrderStatus
import kotlinx.coroutines.tasks.await


class AuthRemoteDataSource(val context: Context) : UserDataSource {

    private val TAG = "AuthRemoteDataSource"

    private val _root by lazy {
        FirebaseFirestore.getInstance()
    }

    private fun usersCollectionRef() = _root.collection(USERS_COLLECTION)

    override suspend fun getUserById(userId: String, onComplete:(UserData?) -> Unit){
        val resRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        if (resRef != null){
            val user = resRef.toObjects(UserData::class.java)[0]
            onComplete(user)
        }else{
            onComplete(null)
        }
    }

    override suspend fun checkPassByUserId(userId: String ,password: String,onComplete: (Boolean) -> Unit) {
        val ref = usersCollectionRef().whereEqualTo(USER_ID_FIELD,userId).get().await()
        if (ref != null){
            val user = ref.toObjects(UserData::class.java)[0]
            Log.i("Login","email: ${user.email}")
            if (user.password == password){
                onComplete(true)
            }else{
                onComplete(false)
            }
        }
    }


    override suspend fun checkUserIsExist(email: String, isExist:(Boolean) -> Unit, onError:(String) -> Unit) {
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
                    onError(context.resources.getString(R.string.no_connection))
                }
            }
    }


    override suspend fun addUser(userData: UserData) {
        usersCollectionRef()
            .document(userData.userId)
            .set(userData)
            .addOnSuccessListener {
                Log.d(TAG, "Doc added")
            }
            .addOnFailureListener { e ->
                Log.d(TAG, "firebase fire store error occurred: $e")
            }
    }

    // TODO: use this function if you want to remove the user from the remote data source
    override suspend fun deleteUser() {
    }


    override suspend fun getUserById(userId: String): UserData =
        usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
            .toObjects(UserData::class.java)[0]


    override suspend fun getOrdersByUserId(userId: String): Result<List<UserData.OrderItem>?> {
        val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        return if (!userRef.isEmpty) {
            val userData = userRef.documents[0].toObject(UserData::class.java)
            Result.Success(userData!!.orders)
        } else {
            Result.Error(Exception("User Not Found!"))
        }
    }

    override suspend fun getLikesByUserId(userId: String): Result<List<String>?> {
        val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        return if (!userRef.isEmpty) {
            val userData = userRef.documents[0].toObject(UserData::class.java)
            Result.Success(userData!!.likes)
        } else {
            Result.Error(Exception("User Not Found!"))
        }
    }

    override suspend fun getUserByMobileAndPassword(
        mobile: String,
        password: String): MutableList<UserData> =
        usersCollectionRef().whereEqualTo(PHONE_FIELD, mobile)
            .whereEqualTo(PASSWORD_FIELD, password).get().await().toObjects(UserData::class.java)




    override suspend fun likeProduct(productId: String, userId: String) {
        val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        if (!userRef.isEmpty) {
            val docId = userRef.documents[0].id
            usersCollectionRef().document(docId)
                .update(LIKES_FIELD, FieldValue.arrayUnion(productId))
        }
    }

    override suspend fun dislikeProduct(productId: String, userId: String) {
        val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        if (!userRef.isEmpty) {
            val docId = userRef.documents[0].id
            usersCollectionRef().document(docId)
                .update(LIKES_FIELD, FieldValue.arrayRemove(productId))
        }
    }


    override suspend fun insertCartItem(newItem: UserData.CartItem, userId: String) {
        val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        if (!userRef.isEmpty) {
            val docId = userRef.documents[0].id
            usersCollectionRef().document(docId)
                .update(CART_FIELD, FieldValue.arrayUnion(newItem.toHashMap()))
        }
    }

    override suspend fun updateCartItem(item: UserData.CartItem, userId: String) {
        val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        if (!userRef.isEmpty) {
            val docId = userRef.documents[0].id
            val oldCart =
                userRef.documents[0].toObject(UserData::class.java)?.cart?.toMutableList()
            val idx = oldCart?.indexOfFirst { it.itemId == item.itemId } ?: -1
            if (idx != -1) {
                oldCart?.set(idx, item)
            }
            usersCollectionRef().document(docId)
                .update(CART_FIELD, oldCart?.map { it.toHashMap() })
        }
    }

    override suspend fun deleteCartItem(itemId: String, userId: String) {
        val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        if (!userRef.isEmpty) {
            val docId = userRef.documents[0].id
            val oldCart =
                userRef.documents[0].toObject(UserData::class.java)?.cart?.toMutableList()
            val idx = oldCart?.indexOfFirst { it.itemId == itemId } ?: -1
            if (idx != -1) {
                oldCart?.removeAt(idx)
            }
            usersCollectionRef().document(docId)
                .update(CART_FIELD, oldCart?.map { it.toHashMap() })
        }
    }

    override suspend fun placeOrder(newOrder: UserData.OrderItem, userId: String) {
        // add order to customer and
        // specific items to their owners
        // empty customers cart
        val ownerProducts: MutableMap<String, MutableList<UserData.CartItem>> = mutableMapOf()
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
                val ownerOrder = UserData.OrderItem(
                    newOrder.orderId,
                    userId,
                    items,
                    itemPrices,
                    newOrder.shippingCharges,
                    newOrder.paymentMethod,
                    newOrder.orderDate,
                    OrderStatus.CONFIRMED.name)

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
                .update(CART_FIELD, ArrayList<UserData.CartItem>())
        }
    }

    override suspend fun setStatusOfOrderByUserId(orderId: String, userId: String, status: String) {
        // update on customer and owner
        val userRef = usersCollectionRef().whereEqualTo(USER_ID_FIELD, userId).get().await()
        if (!userRef.isEmpty) {
            val docId = userRef.documents[0].id
            val ordersList =
                userRef.documents[0].toObject(UserData::class.java)?.orders?.toMutableList()
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
                            custRef.documents[0].toObject(UserData::class.java)?.orders?.toMutableList()
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


    override suspend fun clearUser(userId: String) {
        usersCollectionRef().document(userId).delete()
    }




    companion object {
        private const val USERS_COLLECTION = "users"
        private const val USER_ID_FIELD = "userId"
        private const val LIKES_FIELD = "likes"
        private const val EMAIL_FIELD = "email"
        private const val CART_FIELD = "cart"
        private const val ORDERS_FIELD = "orders"
        private const val PHONE_FIELD = "phone"
        private const val PASSWORD_FIELD = "password"
        private const val EMAIL_MOBILE_DOC = "emailAndMobiles"

        private const val TAG = "AuthRemoteDataSource"
    }
}