package com.shoppingapp.info.screens.product_details

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.setMargins
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.PagerSnapHelper
import com.shoppingapp.info.R
import com.shoppingapp.info.ShoeColors
import com.shoppingapp.info.ShoeSizes
import com.shoppingapp.info.databinding.ProductDetailsBinding
import com.shoppingapp.info.screens.home.HomeViewModel
import com.shoppingapp.info.screens.orders.OrdersViewModel
import com.shoppingapp.info.utils.AddItemErrors
import com.shoppingapp.info.utils.AddObjectStatus
import com.shoppingapp.info.utils.DotsIndicatorDecoration
import com.shoppingapp.info.utils.StoreDataStatus
import org.koin.android.ext.android.bind
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.parameter.parametersOf


class ProductDetails: Fragment() {

    companion object{
        const val TAG = "Product Details"
    }


    private val homeViewModel by sharedViewModel<HomeViewModel>()
    private val viewModel by sharedViewModel<ProductDetailsViewModel> {
        parametersOf(arguments?.getString("productId") as String) // the argument will be fixed in parameter of view model
    }

    private val ordersViewModel by sharedViewModel<OrdersViewModel>()

    private lateinit var productId: String

    private lateinit var binding: ProductDetailsBinding
    private var selectedSize: Int? = null
    private var selectedColor: String? = null


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        binding = DataBindingUtil.inflate(inflater, R.layout.product_details, container, false)
        productId = arguments?.getString("productId") as String


        if (viewModel.isUserSeller()) {
            binding.btnAddProductToCart.visibility = View.GONE
        } else {
            binding.btnAddProductToCart.visibility = View.VISIBLE


            /** button add in cart **/
            binding.btnAddProductToCart.setOnClickListener {
                if (viewModel.isItemInCart.value == true) {
                    navigateToCartFragment()
                } else {
                    onAddToCart()

                }
            }
        }

        binding.btnBack.setOnClickListener {
           findNavController().navigateUp()
        }

        /** button plus quantity **/
        binding.btnCartProductPlus.setOnClickListener {

            val quantity = viewModel.quantity.value!!
            if (quantity >= 1){
                viewModel.setQuantityOfItem(productId, +1)
            }

        }

        /** button minus quantity **/
        binding.btnCartProductMinus.setOnClickListener {
            val quantity = viewModel.quantity.value!!
            if (quantity >= 2){
                viewModel.setQuantityOfItem(productId, -1)
            }
        }

        setObservers()
        viewModel.getProductDetails(productId)

        return binding.root
    }


    override fun onResume() {
        super.onResume()
        viewModel.setLike(productId)
        viewModel.checkIfInCart(productId)
        viewModel.getCartItems(productId)

        selectedSize = null
        selectedColor = null

    }



    private fun setObservers() {

        /** live data data status **/
        viewModel.dataStatus.observe(viewLifecycleOwner) {
            when (it) {
                StoreDataStatus.LOADING ->{}
                StoreDataStatus.DONE -> {
                    binding.proDetailsLayout.visibility = View.VISIBLE
                    setViews()
                }
                StoreDataStatus.ERROR ->{}
                else -> {
                    binding.proDetailsLayout.visibility = View.GONE
                }
            }
        }



        /** live data is liked **/
        viewModel.isProductLiked.observe(viewLifecycleOwner) {
            binding.btnProductDetailsLike.isChecked = it
        }


        /** quantity **/
        viewModel.quantity.observe(viewLifecycleOwner){ quantity ->
            if (quantity != null){
                binding.cartProductQuantity.text = quantity.toString()
            }
        }


        /** live data item in cart **/
        viewModel.isItemInCart.observe(viewLifecycleOwner) {
            if (it != null){
                if (it == true) {
                    binding.btnAddProductToCart.text = getString(R.string.pro_details_go_to_cart_btn_text)
                } else {
                    binding.btnAddProductToCart.text = getString(R.string.pro_details_add_to_cart_btn_text)
                }
            }else{
                   binding.btnAddProductToCart.text = getString(R.string.pro_details_add_to_cart_btn_text)
            }
        }





    }

    @SuppressLint("ResourceAsColor")
    private fun modifyErrors(errList: List<AddItemErrors>) {
        makeToast("Please Select Size and Color.")
        if (!errList.isNullOrEmpty()) {
            errList.forEach { err ->
                when (err) {
                    AddItemErrors.ERROR_SIZE -> {
                        binding.proDetailsSelectSizeLabel.setTextColor(R.color.red_600)
                    }
                    AddItemErrors.ERROR_COLOR -> {
                        binding.proDetailsSelectColorLabel.setTextColor(R.color.red_600)
                    }
                }
            }
        }
    }

    private fun setViews() {

        binding.btnAddProductToCart.visibility = View.VISIBLE

        // set images and dots
        setImagesView()

        binding.productDetailsTitle.text = viewModel.productData.value?.name ?: ""
        binding.btnProductDetailsLike.apply {
            setOnClickListener {
                homeViewModel.toggleLikeByProductId(productId)
            }
        }


        /** set price data **/
        binding.productPrice.text = resources.getString(
            R.string.pro_details_price_value,
            viewModel.productData.value?.price.toString()
        )

        setShoeSizeButtons()
        setShoeColorsButtons()

        /** set product description  **/
        binding.productDescription.text = viewModel.productData.value?.description ?: ""


    }

    private fun onAddToCart() {
        viewModel.addToCart(selectedSize, selectedColor,productId)
    }


    private fun navigateToCartFragment() {
        findNavController().navigate(R.id.action_productDetails_to_cart)
    }

    private fun makeToast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    private fun setImagesView() {
        if (context != null) {
            binding.proDetailsImagesRecyclerview.isNestedScrollingEnabled = false
            val adapter = ProductImagesAdapter(
                requireContext(),
                viewModel.productData.value?.images ?: emptyList()
            )
            binding.proDetailsImagesRecyclerview.adapter = adapter
            val rad = resources.getDimension(R.dimen.radius)
            val dotsHeight = resources.getDimensionPixelSize(R.dimen.dots_height)
            val inactiveColor = ContextCompat.getColor(requireContext(), R.color.gray)
            val activeColor = ContextCompat.getColor(requireContext(), R.color.blue_accent_300)
            val itemDecoration = DotsIndicatorDecoration(rad, rad * 4, dotsHeight, inactiveColor, activeColor)
            binding.proDetailsImagesRecyclerview.addItemDecoration(itemDecoration)
            PagerSnapHelper().attachToRecyclerView(binding.proDetailsImagesRecyclerview)
        }
    }

    private fun setShoeSizeButtons() {
        binding.proDetailsSizesRadioGroup.apply {
            for ((_, v) in ShoeSizes) {
                if (viewModel.productData.value?.availableSizes?.contains(v) == true) {
                    val radioButton = RadioButton(context)
                    radioButton.id = v
                    radioButton.tag = v
                    val param = binding.proDetailsSizesRadioGroup.layoutParams as ViewGroup.MarginLayoutParams
                    param.setMargins(resources.getDimensionPixelSize(R.dimen.radio_margin_size))
                    param.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    param.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    radioButton.layoutParams = param
                    radioButton.background = ContextCompat.getDrawable(context, R.drawable.radio_selector)
                    radioButton.setButtonDrawable(R.color.transparent)
                    radioButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14F)
                    radioButton.setTextColor(Color.BLACK)
                    radioButton.setTypeface(null, Typeface.BOLD)
                    radioButton.textAlignment = View.TEXT_ALIGNMENT_CENTER
                    radioButton.text = "$v"
                    radioButton.setOnCheckedChangeListener { buttonView, isChecked ->
                        val tag = buttonView.tag.toString().toInt()
                        if (isChecked) {
                            selectedSize = tag
                        }
                    }
                    addView(radioButton)
                }
            }
            invalidate()
        }
    }

    private fun setShoeColorsButtons() {
        binding.proDetailsColorsRadioGroup.apply {
            var ind = 1
            for ((k, v) in ShoeColors) {
                if (viewModel.productData.value?.availableColors?.contains(k) == true) {
                    val radioButton = RadioButton(context)
                    radioButton.id = ind
                    radioButton.tag = k
                    val param =
                        binding.proDetailsColorsRadioGroup.layoutParams as ViewGroup.MarginLayoutParams
                    param.setMargins(resources.getDimensionPixelSize(R.dimen.radio_margin_size))
                    param.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    param.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    radioButton.layoutParams = param
                    radioButton.background =
                        ContextCompat.getDrawable(context, R.drawable.color_radio_selector)
                    radioButton.setButtonDrawable(R.color.transparent)
                    radioButton.backgroundTintList = ColorStateList.valueOf(Color.parseColor(v))
                    if (k == "white") {
                        radioButton.backgroundTintMode = PorterDuff.Mode.MULTIPLY
                    } else {
                        radioButton.backgroundTintMode = PorterDuff.Mode.ADD
                    }
                    radioButton.setOnCheckedChangeListener { buttonView, isChecked ->
                        val tag = buttonView.tag.toString()
                        if (isChecked) {
                            selectedColor = tag
                        }
                    }
                    addView(radioButton)
                    ind++
                }
            }
            invalidate()
        }
    }
}
