package com.shoppingapp.info.screens.add_edit_product

import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.*
import com.shoppingapp.info.data.Product
import com.shoppingapp.info.repository.product.ProductRepository
import com.shoppingapp.info.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import com.shoppingapp.info.utils.Result



class AddEditProductViewModel(private val productRepository: ProductRepository): ViewModel() {

    private val userId = productRepository.sharePrefManager.getUserIdFromSession()!!

    companion object{
        private const val TAG = "AddEditProductViewModel"
    }

//    private val appShop = ShoppingApplication(application)
//    private val productsRepository by lazy { appShop.productRepository }
//    private val sessionManager = SharePrefManager(application.applicationContext)


    private val _selectedCategory = MutableLiveData<String>()
    val selectedCategory: LiveData<String> get() = _selectedCategory

    private val _productId = MutableLiveData<String>()
    val productId: LiveData<String> get() = _productId

    private val _isEdit = MutableLiveData<Boolean>()
    val isEdit: LiveData<Boolean> get() = _isEdit

    private val _errorStatus = MutableLiveData<AddProductViewErrors>()
    val errorStatus: LiveData<AddProductViewErrors> get() = _errorStatus

    private val _dataStatus = MutableLiveData<StoreDataStatus>()
    val dataStatus: LiveData<StoreDataStatus> get() = _dataStatus

    private val _addProductErrors = MutableLiveData<AddProductErrors?>()
    val addProductErrors: LiveData<AddProductErrors?> get() = _addProductErrors

    private val _productData = MutableLiveData<Product>()
    val productData: LiveData<Product> get() = _productData

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val newProductData = MutableLiveData<Product>()

    init {
        _errorStatus.value = AddProductViewErrors.NONE
    }

    fun setEditState(state: Boolean) {
        _isEdit.value = state
    }

    fun setCategory(catName: String) {
        _selectedCategory.value = catName
    }


    fun deleteProduct(productId: String,onSuccess:(Boolean)-> Unit) {
        viewModelScope.launch {
            val delRes = async { productRepository.deleteProductById(productId) }
            when (val res = delRes.await()) {
                is Result.Success ->{
                    Log.d(TAG, "onDelete: Success")
                    onSuccess(true)
                }
                is Error -> Log.d(TAG, "onDelete: Error, ${res.message}")
                else -> Log.d(TAG, "onDelete: Some error occurred!")
            }
        }
    }


    fun setProductData(productId: String) {
        _productId.value = productId
        viewModelScope.launch {
            Log.d(TAG, "onLoad: Getting product Data")
            _dataStatus.value = StoreDataStatus.LOADING
            val res = async { productRepository.getProductById(productId, forceUpdate = false) }
            val proRes = res.await()
            if (proRes is Result.Success) {
                val proData = proRes.data
                _productData.value = proData
                _selectedCategory.value = _productData.value!!.category
                Log.d(TAG, "onLoad: Successfully retrieved product data")
                _dataStatus.value = StoreDataStatus.DONE
            } else if (proRes is Error) {
                _dataStatus.value = StoreDataStatus.ERROR
                Log.d(TAG, "onLoad: Error getting product data")
                _productData.value = Product()
            }
        }
    }

    fun submitProduct(
        name: String,
        price: Double?,
        mrp: Double?,
        desc: String,
        sizes: List<Int>,
        colors: List<String>,
        imgList: List<Uri>,
    ) {
        if (name.isBlank() || price == null || mrp == null || desc.isBlank() || sizes.isNullOrEmpty() || colors.isNullOrEmpty() || imgList.isNullOrEmpty()) {
            _errorStatus.value = AddProductViewErrors.EMPTY
        } else {
            if (price == 0.0 || mrp == 0.0) {
                _errorStatus.value = AddProductViewErrors.ERR_PRICE_0
            } else {
                _errorStatus.value = AddProductViewErrors.NONE
                val proId = if (_isEdit.value == true) _productId.value!! else
                    getProductId(userId, selectedCategory.value!!)
                val newProduct =
                    Product(
                        proId,
                        name.trim(),
                        userId,
                        desc.trim(),
                        _selectedCategory.value!!,
                        price,
                        mrp,
                        sizes,
                        colors,
                        emptyList(),
                        0.0
                    )
                newProductData.value = newProduct
                Log.d(TAG, "pro = $newProduct")
                if (_isEdit.value == true) {
                    updateProduct(imgList)
                } else {
                    insertProduct(imgList)
                }
            }
        }
    }

    private fun updateProduct(imgList: List<Uri>) {
        viewModelScope.launch {
            if (newProductData.value != null && _productData.value != null) {
                _addProductErrors.value = AddProductErrors.ADDING
                val resImg =
                    async { productRepository.updateImages(imgList, _productData.value!!.images) }
                val imagesPaths = resImg.await()
                newProductData.value?.images = imagesPaths
                if (newProductData.value?.images?.isNotEmpty() == true) {
                    if (imagesPaths[0] == ERR_UPLOAD) {
                        Log.d(TAG, "error uploading images")
                        _addProductErrors.value = AddProductErrors.ERR_ADD_IMG
                    } else {
                        val updateRes =
                            async { productRepository.updateProduct(newProductData.value!!) }
                        val res = updateRes.await()
                        if (res is Result.Success) {
                            Log.d(TAG, "onUpdate: Success")
                            _addProductErrors.value = AddProductErrors.NONE
                        } else {
                            Log.d(TAG, "onUpdate: Some error occurred!")
                            _addProductErrors.value = AddProductErrors.ERR_ADD
                            if (res is Error)
                                Log.d(TAG, "onUpdate: Error, ${res.message}")
                        }
                    }
                } else {
                    Log.d(TAG, "Product images empty, Cannot Add Product")
                }
            } else {
                Log.d(TAG, "Product is Null, Cannot Add Product")
            }
        }
    }

    private fun insertProduct(imgList: List<Uri>) {
        viewModelScope.launch {
            if (newProductData.value != null) {
                _addProductErrors.value = AddProductErrors.ADDING
                val resImg = async { productRepository.insertImages(imgList) }
                val imagesPaths = resImg.await()
                newProductData.value?.images = imagesPaths
                if (newProductData.value?.images?.isNotEmpty() == true) {
                    if (imagesPaths[0] == ERR_UPLOAD) {
                        Log.d(TAG, "error uploading images")
                        _addProductErrors.value = AddProductErrors.ERR_ADD_IMG
                    } else {
                        val deferredRes = async {
                            productRepository.insertProduct(newProductData.value!!)
                        }
                        val res = deferredRes.await()
                        if (res is Result.Success) {
                            Log.d(TAG, "onInsertProduct: Success")
                            _addProductErrors.value = AddProductErrors.NONE
                        } else {
                            _addProductErrors.value = AddProductErrors.ERR_ADD
                            if (res is Error)
                                Log.d(TAG, "onInsertProduct: Error Occurred, ${res.message}")
                        }
                    }
                } else {
                    Log.d(TAG, "Product images empty, Cannot Add Product")
                }
            } else {
                Log.d(TAG, "Product is Null, Cannot Add Product")
            }
        }
    }

}